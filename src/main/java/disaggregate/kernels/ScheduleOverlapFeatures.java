package disaggregate.kernels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import disaggregate.model.Activity;
import disaggregate.model.Person;
import disaggregate.model.ActivityNode;
import disaggregate.model.ActivityNode;
import disaggregate.rvu.Trip;
import se.vti.roundtrips.simulator.IntervalUtils;
import se.vti.roundtrips.simulator.StayEpisode;
import se.vti.roundtrips.single.RoundTrip;
import se.vti.utils.misc.Tuple;

public class ScheduleOverlapFeatures implements Features {
    private final Map<Activity, List<Tuple<Double, Double>>> activityIntervals;

    public ScheduleOverlapFeatures(Map<Activity, List<Tuple<Double, Double>>> activityIntervals) {
        this.activityIntervals = activityIntervals;
    }

    public Map<Activity, List<Tuple<Double, Double>>> getActivityIntervals() {
        return activityIntervals;
    }

    private static Map<Activity, List<Tuple<Double, Double>>> createEmptyActivityIntervals() {
        Map<Activity, List<Tuple<Double, Double>>> map = new HashMap<>();
        for (Activity act : Activity.values()) {
            map.put(act, new ArrayList<>());
        }
        return map;
    }

    public static ScheduleOverlapFeatures from(Person p) {
        return from(p, 24.0);
    }

    public static ScheduleOverlapFeatures from(Person p, double horizon_h) {
        Trip trip = p.getTrip();
        List<Trip.Leg> legs = trip.getLegs();
        Map<Activity, List<Tuple<Double, Double>>> activityMap = createEmptyActivityIntervals();

        // Add initial HOME episode: [0, first departure]. This assumes first activity is a home activity in the observations and needs to be generalized / handled differently if this is not the case. 
        if (!legs.isEmpty()) {
            double firstDep_h = legs.get(0).start / 60.0;
            firstDep_h = Math.max(0.0, Math.min(horizon_h, firstDep_h));
            addInterval(activityMap, Activity.HOME, 0.0, firstDep_h);
        }

        // Add interval for each destination activity occurrence
        for (int ii = 0; ii < legs.size(); ii++) {
            Trip.Leg leg = legs.get(ii);
            Activity activity = leg.destinationActivity;

            double start_h = leg.end / 60.0;
            double end_h = (ii < legs.size() - 1) ? (legs.get(ii + 1).start / 60.0) : horizon_h;

            // clamp observed times into [0, horizon].
            start_h = Math.max(0.0, Math.min(horizon_h, start_h));
            end_h   = Math.max(0.0, Math.min(horizon_h, end_h));

            addInterval(activityMap, activity, start_h, end_h);
        }

        return new ScheduleOverlapFeatures(activityMap);
    }

    public static ScheduleOverlapFeatures from(RoundTrip<? extends ActivityNode> rt) {
        return from(rt, 24.0, Map.of());
    }

    /**
     * @param accruingNodes for activities restricted to a single node, the node that counts.
     *                      An activity absent from the map is not restricted and all of its
     *                      stays are included.
     */
    public static  ScheduleOverlapFeatures from(RoundTrip<? extends ActivityNode> rt, double horizon_h, Map<Activity, ActivityNode> accruingNodes) {
        Map<Activity, List<Tuple<Double, Double>>> activityMap = createEmptyActivityIntervals();

        var intervalUtils = new IntervalUtils(24.0);

        for (Activity act : Activity.values()) {
            ActivityNode accruingNode = accruingNodes.get(act);
            List<Tuple<Double, Double>> intervals = intervalUtils.extractIntervals(
                rt.getEpisodes(),
                ep -> (ep instanceof StayEpisode<?> se)
                && (
                    ((ActivityNode) se.getLocation()).getPrimaryActivity().equals(act)
                    && ((accruingNode == null) || (accruingNode.equals(se.getLocation()))))
                );
            activityMap.put(act, intervals);
        }

        return new ScheduleOverlapFeatures(activityMap);
    }

    private static void addInterval(Map<Activity, List<Tuple<Double, Double>>> intervalsMap,
                                    Activity activity, double start_h, double end_h) {
        if (!(end_h > start_h)) return;
        intervalsMap.computeIfAbsent(activity, k -> new ArrayList<>())
                    .add(new Tuple<>(start_h, end_h));
    }
}