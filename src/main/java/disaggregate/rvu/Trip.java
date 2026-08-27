package disaggregate.rvu;

import java.util.*;

import common.Activity;

public class Trip {
        public final String[] TRIP_VARIABLES = {"idx", "ttt", "nStops", "legs"};
        private String id;
        private List<Leg> legs;
        private Double cachedTotalTravelTime;
        private Integer cachedStopCount;

        public class Leg implements Comparable<Leg> {
            public final String from; 
            public final String to;
            public final double start; 
            public final double end;
            public final double duration;
            public final Activity destinationActivity;

            // Leg(Map<String, String> row) {
            //     this(row, false);
            // }
            Leg(Map<String, String> row, boolean usingRVUCodes) {
                this(
                    row.get("D_A_S"), 
                    row.get("D_B_S"),
                    Trip.clockTimeToMinutes(row.get("D_A_KL")),
                    Trip.clockTimeToMinutes(row.get("D_B_KL")),
                    Activity.fromString(String.valueOf(row.get("activity")))
                    );
            }

            Leg(String from, String to, double start, double end, Activity destinationActivity) {
                this.from = from; 
                this.to = to; 
                this.start = start; 
                this.end = (end < start) ? end + 24.0 * 60.0 : end; 
                // this.duration = Trip.computeDuration(start, end);
                this.duration = this.end - this.start;
                this.destinationActivity = destinationActivity; 
            }

            @Override
            public int compareTo(Leg other) {
                return Double.compare(this.start, other.start);
            }

            public String toString() {
                return "'from': " +  "'" + this.from + "'" +
                    ", 'to': " +  "'" + this.to + "'" +
                    ", 'start': " + this.start + 
                    ", 'end': " + this.end + 
                    ", 'duration': " + this.duration + 
                    ", 'destinationActivity': '" + this.destinationActivity + "'";
            }
        }
    public Trip(List<Map<String, String>> rows) {
            this(rows, false);
        }
    public Trip(List<Map<String, String>> rows, boolean usingRVUCodes) {
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Rows cannot be empty");
        }
        for (Map<String, String> row : rows) {
            if (usingRVUCodes) {
                if (!row.containsKey("D_ARE")) {
                    throw new IllegalArgumentException("Data must contain D_ARE columns if using RVU codes");
                }
            } else {
                if (!row.containsKey("activity")) {
                    throw new IllegalArgumentException("Data must contain activity columns if not using RVU codes");
                }
            }

        }

        this.id = rows.get(0).get("UENR").toString();
        this.legs = this.createLegs(rows, usingRVUCodes);
        // this.legs = new ArrayList<>(rows);
    }

        public Double getTotalTravelTime_m() {
            if (cachedTotalTravelTime == null) {
                cachedTotalTravelTime = 0.0;
                for (Leg leg : legs) {
                    try {
                        cachedTotalTravelTime += leg.duration;
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid travel time format in leg: " + leg);
                    }
                }
            }
            return cachedTotalTravelTime;
        }

        public double getTotalTravelTime_h() {
            return getTotalTravelTime_m() / 60;
        }

        private void setTotalTravelTime_h(double ttt) {
            this.cachedTotalTravelTime = ttt * 60;
        }

        private void setTotalTravelTime_m(double ttt) {
            this.cachedTotalTravelTime = ttt;
        }

        private static double clockTimeToMinutes(String timeStr) {
            assert timeStr.length() <= 4;
            if (timeStr.length() < 4) {
                timeStr = String.format("%04d", Integer.parseInt(timeStr));
            }

            try {
                double hours = Double.parseDouble(timeStr.substring(0, 2));
                double minutes = Double.parseDouble(timeStr.substring(2, 4));
                return hours * 60 + minutes;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid time format: " + timeStr);
            } 
        }

        public Map<Activity, Double> getActivityTimes_h() {
            Map<Activity, Double> res = new EnumMap<>(Activity.class);

            for (Activity a: Activity.values()) {
                res.put(a, getActivityTime_h(a));
            }

            return res;
        }

        public double getActivityTime_h(Activity act) {
            return (double) getActivityTime_m(act) / 60.0;
        }

        public double getActivityTime_m(Activity act) {
            double res = 0.0;
            for (int ii = 0; ii < legs.size() - 1; ii++) {
                Leg leg = legs.get(ii);
                if (leg.destinationActivity.equals(act)) {
                    double dur = legs.get(ii+1).start - leg.end;
                    if (dur < 0) {
                        throw new IllegalArgumentException("Negative durations are not allowed!");
                    }
                    res += dur;
                }
            }
            
            // Handle time before first departure & time after last home arrival as home time
            if (act.equals(Activity.HOME)) {
                res += legs.get(0).start;
                res += 1440 - legs.get(legs.size() - 1).end;
            }

            if (res < 0) {
                throw new IllegalArgumentException("Negative activity time computed for activity: " + act);
            }

            return res;
        }

        public Integer getStopCount() {
            if (cachedStopCount == null) {
                cachedStopCount = legs.size();
            }
            return cachedStopCount;
        }

        public void setStopCount(int stops) {
            this.cachedStopCount = stops;
        }

        public int size() {
            return legs.size();
        }

        public List<Leg> createLegs(List<Map<String, String>> legRows) {
            return createLegs(legRows, false);
        }

        public List<Leg> createLegs(List<Map<String, String>> legRows, boolean usingRVUCodes) {
            List<Leg> legs = new ArrayList<>(legRows.size());
            for (Map<String, String> row : legRows) {
                legs.add(new Leg(row, usingRVUCodes));
            }
            Collections.sort(legs);
            return legs;
        }   

        public List<Leg> getLegs() {
            return Collections.unmodifiableList(legs);
        }

        public Leg getLeg(int index) {
            if (index < 0 || index >= legs.size()) {
                throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + legs.size());
            }
            return legs.get(index);
        }

        public String getId() {
            return id;
        }

        public List<Activity> getActivities() {
            List<Activity> activities = new ArrayList<>();
            for (Leg leg : legs) {
                activities.add(leg.destinationActivity);
            }
            return activities;
        }

        private static double gapMinutes(double end, double nextStart) {
            double dayMin = 24.0 * 60.0;
            if (Double.isNaN(end) || Double.isNaN(nextStart)) return Double.NaN;
            if (nextStart < end) nextStart += dayMin;
            return Math.max(0.0, nextStart - end);
        }

        public Map<Activity, Double> getActivityDurations_h() {
            Map<Activity, Double> durations = getActivityDurations_m();
            for  (Activity act : durations.keySet()) {
                durations.put(act, durations.get(act) / 60.0);
            }
            return durations;
        }

        public Map<Activity, Double> getActivityDurations_m() {
            double DayMin = 24.0 * 60.0;
            Map<Activity, Double> totals = new java.util.EnumMap<>(Activity.class);
            for (Activity act : Activity.values()) totals.put(act, 0.0);
            if (legs.isEmpty()) return totals;
            boolean homeBeforeFirst = true;
            boolean homeAfterLast = true;

            // We can assume that the person is at home before their first departure.
            if (homeBeforeFirst) {
                double firstStart = legs.get(0).start;
                if (!Double.isNaN(firstStart) && firstStart > 0) {
                    totals.merge(Activity.HOME, firstStart, Double::sum);
                }
            }

            for (int ii = 0; ii < legs.size() - 1; ii++) {
                Leg cur = legs.get(ii);
                double curEnd = cur.end;
                double nextStart = legs.get(ii+1).start;
                if (nextStart < curEnd) nextStart += DayMin;
                double g = Math.max(0.0, nextStart - curEnd);
                totals.merge(cur.destinationActivity, g, Double::sum);
            }

            // The person should return home at the end.
            if (homeAfterLast) {
                var last = legs.get(legs.size() - 1);
                if (!Double.isNaN(last.end)) {
                    double endAdj = last.end % DayMin;
                    double tail = (endAdj <= DayMin) ? (DayMin - endAdj) : 0.0;
                    if (tail > 0 && tail < DayMin) totals.merge(Activity.HOME, tail, Double::sum);
                }
            }

            return totals;
    }

        @Override
        public String toString() {
            return String.format("Trip{Id='%s', Legs='%d', TTT='%.2f' hours.", id, legs.size(), getTotalTravelTime_h());
        }

        public String toXML() {
            StringBuilder sb = new StringBuilder();
            sb.append("<Trip id=\"").append(id).append("\"")
              .append(" totalTravelTime_h=\"").append(String.format(Locale.US, "%.2f", getTotalTravelTime_h())).append("\"")
            //   .append(" activityTime_h=\"").append(String.format(Locale.US, "%.2f", getActivityTime_h())).append("\"")
              .append(" nStops=\"").append(getStopCount()).append("\"/>\n");
            return sb.toString();
        }

        public List<String> getLocationIds() {
            List<String> locationIds = new ArrayList<>();
            for (Leg leg : legs) {
                locationIds.add(leg.to);
            }
            return locationIds;
        }

        // Getters and toString() method

    public Trip(String id, double ttt_h, int nStops, java.util.List<Activity> activities) {
        if (nStops < 0) {
            throw new IllegalArgumentException("nStops cannot be negative");
        }
        if (activities == null) {
            activities = java.util.List.of();
        }
        if (activities.size() != nStops) {
            throw new IllegalArgumentException("activities.size() must equal nStops");
        }

        this.id = id;
        this.legs = new java.util.ArrayList<>(nStops);

        double ttt_m = ttt_h * 60.0;
        this.cachedTotalTravelTime = ttt_m;
        this.cachedStopCount = nStops;

        // create dummy legs so getActivities()/getLegs() still work
        for (int i = 0; i < nStops; i++) {
            Activity act = activities.get(i);
            String fromId = "L" + i;
            String toId   = "L" + (i + 1);
            Leg leg = new Leg(fromId, toId, 0.0, 0.0, act); // duration = 0
            this.legs.add(leg);
        }
    }
    }