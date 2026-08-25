package disaggregate.kernels;

import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import disaggregate.model.Activity;
import disaggregate.model.ActivityNode;
import disaggregate.utils.summaries.UniqueActivityNodeSummary;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.multiple.MultiRoundTripSummary;
import se.vti.roundtrips.simulator.Episode;
import se.vti.roundtrips.simulator.MoveEpisode;
import se.vti.roundtrips.simulator.StayEpisode;
import se.vti.roundtrips.single.RoundTrip;
import se.vti.utils.misc.Tuple;

public class KernelSummary implements MultiRoundTripSummary<ActivityNode> {
    private static final Logger logger = LogManager.getLogger(KernelSummary.class);
    
    private boolean trackActivities = false; 
    private boolean trackLocations = false; 
    private boolean computeTtt = false; 
    private boolean trackActivityDurations = false;
    private boolean trackActivityStartTimes = false;
    private boolean trackActivityIntervals = false; // For schedule overlap kernel
    private boolean trackNumStops = false;

    private double[][] kernelCache;
    private boolean[][] kernelCacheValid;
    private int nextKernelIndex = 0;
    
    private final Map<Integer, Set<Activity>> activitiesPerRT;
    private final Map<Integer, List<ActivityNode>> locationsPerRT;
    private final Map<Integer, Double> tttPerRT;
    private final Map<Integer, Integer> nStopsPerRT;
    private final Map<Integer, Map<Activity, Double>> activityStartTimesPerRT;
    private final Map<Integer, Map<Activity, Double>> activityDurationsPerRT;
    private final Map<Integer, Map<Activity, List<Tuple<Double, Double>>>> activityIntervalsPerRT; // For schedule overlap kernel. Stores list of intervals for each activity to handle multiple occurrences.
    private final int populationSize; 
    private final boolean[][] excludedPairs;

    // Rebound from the containing MultiRoundTrip on every update; null means the
    // unique-activity-node feature is not switched on for this run.
    private UniqueActivityNodeSummary uniqueActivityNodesSummary;

    private double totalLW;

//    private final Map<Kernel, Map<Integer, Double>> kernelValueCache;

    private KernelSummary(int populationSize, boolean[][] excludedPairs, boolean skipInit) {
        this.excludedPairs = excludedPairs;
        this.populationSize = populationSize;
        this.totalLW = 0.0;
        this.activitiesPerRT = new HashMap<>(0);
        this.locationsPerRT = new HashMap<>(0);
        this.tttPerRT = new HashMap<>(0);
        this.nStopsPerRT = new HashMap<>(0);
        this.activityDurationsPerRT = new HashMap<>(0);
        this.activityStartTimesPerRT = new HashMap<>(0);
        this.activityIntervalsPerRT = new HashMap<>(0);
    }

    public KernelSummary(int populationSize, boolean[][] excludedPairs) {
        this.excludedPairs = excludedPairs;
        this.populationSize = populationSize;
        this.totalLW = 0.0;

        this.activitiesPerRT = new HashMap<>(populationSize);
        this.locationsPerRT = new HashMap<>(populationSize);
        this.tttPerRT = new HashMap<>(populationSize);
        this.nStopsPerRT = new HashMap<>(populationSize);
        this.activityDurationsPerRT = new HashMap<>(populationSize);
//        this.kernelValueCache = new HashMap<>();
        this.activityStartTimesPerRT = new HashMap<>(populationSize);
        this.activityIntervalsPerRT = new HashMap<>(populationSize);
    }

    public void trackActivities() { this.trackActivities = true; }
    public void trackLocations() { this.trackLocations = true; }
    public void setTrackActivityIntervals(boolean val) { this.trackActivityIntervals = val; }
    public void setTrackActivityDurations(boolean val) { this.trackActivityDurations = val; }
    public void setTrackNumStops(boolean val) { this.trackNumStops = val; }
    public void setTrackTtt(boolean val) { this.computeTtt = val; }
    public void trackActivityStartTimes() { this.trackActivityStartTimes = true; }
    public void trackActivityIntervals() { this.trackActivityIntervals = true; }

    // GETTERS
    public double getTotalLW() { return this.totalLW; }

    // CACHING
    public int registerKernel() { return nextKernelIndex++; }


    public void initCache() {
        kernelCache = new double[nextKernelIndex][populationSize];
        kernelCacheValid = new boolean[nextKernelIndex][populationSize];
    }

    public boolean isCacheValid(int kernelIdx, int rtIdx) {
        if (kernelCacheValid == null) return false;
        return kernelCacheValid[kernelIdx][rtIdx];
    }

    public double getKernelValue(int kernelIdx, int rtIdx) {
        return kernelCache[kernelIdx][rtIdx];
//        return kernelCacheValid[kernelIndex][rtIndex]
//                ? kernelCache[kernelIndex][rtIndex]
//                : null;
    }

    public void setKernelValue(int kernelIndex, int rtIndex, double value) {
        if (kernelCache == null) return;
        kernelCache[kernelIndex][rtIndex] = value;
        kernelCacheValid[kernelIndex][rtIndex] = true;
    }


    // END CACHING


    @Override
    public void clear() {
        activitiesPerRT.clear();
        locationsPerRT.clear();
        tttPerRT.clear();
        nStopsPerRT.clear();
        activityStartTimesPerRT.clear();
        activityDurationsPerRT.clear();
        activityIntervalsPerRT.clear();
//        kernelValueCache.clear();
    }

    @Override
    public void update(RoundTrip<ActivityNode> newRoundTrip, MultiRoundTrip<ActivityNode> mrt) {
        this.uniqueActivityNodesSummary = mrt.getSummary(UniqueActivityNodeSummary.class);
        int idx = newRoundTrip.getIndex();
        update(idx, mrt.getRoundTrip(idx), newRoundTrip);
    }

    @Override 
    public void update(int idx, RoundTrip<ActivityNode> oldRoundTrip, RoundTrip<ActivityNode> newRoundTrip) {
        activitiesPerRT.remove(idx);
        locationsPerRT.remove(idx);
        tttPerRT.remove(idx);
        nStopsPerRT.remove(idx);
        activityDurationsPerRT.remove(idx);
        activityStartTimesPerRT.remove(idx);
        activityIntervalsPerRT.remove(idx);

        // Clearing out the cached kernel values for each kernel-rt pair (kernel instances are per response)
        if (kernelCacheValid != null) {
            for (int k = 0; k < nextKernelIndex; k++) {
                kernelCacheValid[k][idx] = false;
            }
        }

        if (newRoundTrip != null) {
            // Single pass through episodes - extract everything that's tracked
            Set<Activity> activities = trackActivities ? new HashSet<>() : null;
            List<ActivityNode> locations = trackLocations ? new ArrayList<>() : null;

            double ttt = 0.0;
            Map<Activity, List<Tuple<Double, Double>>> actIntervals = new EnumMap<>(Activity.class);

            if (trackActivityStartTimes) {  // SEEMS TO WORK 
                Map<Activity, Double> startTimes = new EnumMap<>(Activity.class);
                double currentTime_h = 0.0; 
                for (Episode ep : newRoundTrip.getEpisodes()) {
                    if (ep instanceof StayEpisode<?> stayEp) {
                        ActivityNode stayLocation = (ActivityNode) stayEp.getLocation();
                        Activity act = stayLocation.getPrimaryActivity();
                        if (!startTimes.containsKey(act) && !act.equals(Activity.HOME)) {
                            startTimes.put(act, currentTime_h);
                        }
                    }
                    currentTime_h += ep.getDuration_h();
                }
                activityStartTimesPerRT.put(idx, startTimes);
            }

            for (Episode e : newRoundTrip.getEpisodes()) {
                if (e instanceof StayEpisode<?> se) {
                    @SuppressWarnings("unchecked")
                    ActivityNode location = (ActivityNode) se.getLocation();
                    
                    if (trackLocations) locations.add(location);
                    if (trackActivities && location != null) {
                        activities.addAll(location.getAllActivities());
                    }
                    
                } else if (e instanceof MoveEpisode<?> && computeTtt) {
                    ttt += e.getDuration_h();
                }
            }

            if (trackActivityDurations) {
                Map<Activity, Double> actDurations = new EnumMap<>(Activity.class);
                for (int ii = 0; ii < newRoundTrip.size(); ii+=2) {
                    StayEpisode<?> se = (StayEpisode<?>) newRoundTrip.getEpisodes().get(ii);
                    ActivityNode location = (ActivityNode) se.getLocation();
                    for (Activity act : location.getAllActivities()) {
                        double prevDuration = actDurations.getOrDefault(act, 0.0);
                        actDurations.put(act, prevDuration + se.getDuration_h());
                    }
                }
                activityDurationsPerRT.put(idx, actDurations);
            }

            if (trackActivityIntervals) {
                Map<Activity, ActivityNode> accruingNodes = (uniqueActivityNodesSummary != null) 
                    ? uniqueActivityNodesSummary.getRoundTripActivityNodes(idx)
                    : Map.of();
                
                // TODO: Hard-coded horizon!
                Map<Activity, List<Tuple<Double, Double>>> extractedIntervals = ActivityIntervals.from(newRoundTrip, 24.0, accruingNodes);
                for (Activity act : Activity.values()) {

                    List<Tuple<Double, Double>> intervals = extractedIntervals.get(act);
                    actIntervals.put(act, intervals);
                }
            }
            
            if (trackActivities) activitiesPerRT.put(idx, activities);
            if (trackLocations) locationsPerRT.put(idx, locations);
            if (computeTtt) tttPerRT.put(idx, ttt);
            if (trackNumStops) nStopsPerRT.put(idx, newRoundTrip.size());
            if (trackActivityIntervals) activityIntervalsPerRT.put(idx, actIntervals);
        }
    }

    @Override 
    public MultiRoundTripSummary<ActivityNode> clone() {
        final KernelSummary copy = new KernelSummary(this.populationSize, this.excludedPairs, true);
        
        copy.trackActivities = this.trackActivities;
        copy.trackLocations = this.trackLocations;
        copy.computeTtt = this.computeTtt;
        copy.trackNumStops = this.trackNumStops;
        copy.trackActivityDurations = this.trackActivityDurations;
        copy.trackActivityStartTimes = this.trackActivityStartTimes;
        copy.trackActivityIntervals = this.trackActivityIntervals;

        // uniqueActivityNodesSummary is deliberately not copied: it is rebound from the
        // containing MultiRoundTrip on every update, so a clone resolves its own.

        copy.tttPerRT.putAll(this.tttPerRT);
        copy.nStopsPerRT.putAll(this.nStopsPerRT);
        copy.activityDurationsPerRT.putAll(this.activityDurationsPerRT);
        copy.activityStartTimesPerRT.putAll(this.activityStartTimesPerRT);
        copy.activitiesPerRT.putAll(this.activitiesPerRT);
        copy.activityIntervalsPerRT.putAll(this.activityIntervalsPerRT);
        copy.locationsPerRT.putAll(this.locationsPerRT);

        copy.nextKernelIndex = this.nextKernelIndex;
        if (this.kernelCache != null) {
            copy.kernelCache = new double[nextKernelIndex][populationSize];
            copy.kernelCacheValid = new boolean[nextKernelIndex][populationSize];
            for (int k = 0; k < nextKernelIndex; k++) {
                System.arraycopy(this.kernelCache[k], 0, copy.kernelCache[k], 0, populationSize);
                System.arraycopy(this.kernelCacheValid[k], 0, copy.kernelCacheValid[k], 0, populationSize);
            }
        }
        
        return copy;
    }

    public Set<Activity> getActivities(RoundTrip<ActivityNode> roundTrip) {
        Set<Activity> result = activitiesPerRT.get(roundTrip.getIndex());
        if (result == null) {
            throw new IllegalStateException(
                "No activities data for RoundTrip index " + roundTrip.getIndex() + 
                ". Summary has " + activitiesPerRT.size() + 
                " entries. trackActivities=" + trackActivities);
        }
        return Collections.unmodifiableSet(result);
    }

    public Map<Activity, Double> getActivityDurations(RoundTrip<ActivityNode> roundTrip) {
        Map<Activity, Double> result = activityDurationsPerRT.get(roundTrip.getIndex());
        if (result == null) {
            throw new IllegalStateException(
                "No activity durations data for RoundTrip index " + roundTrip.getIndex() + 
                ". Summary has " + activityDurationsPerRT.size() + 
                " entries. computeActivityDurations=" + trackActivityDurations);
        }
        return Collections.unmodifiableMap(result);
    }

    public int getNStops(RoundTrip<ActivityNode> roundTrip) {
        Integer result = nStopsPerRT.get(roundTrip.getIndex());
        if (result == null) {
            throw new IllegalStateException(
                "No nStops data for RoundTrip index " + roundTrip.getIndex() + 
                ". Summary has " + nStopsPerRT.size() + 
                " entries. trackNumStops=" + trackNumStops);
        }
        return result;
    }

    public List<ActivityNode> getLocations(RoundTrip<ActivityNode> roundTrip) {
        List<ActivityNode> result = locationsPerRT.get(roundTrip.getIndex());
        if (result == null) {
            throw new IllegalStateException(
                "No locations data for RoundTrip index " + roundTrip.getIndex() + 
                ". Summary has " + locationsPerRT.size() + 
                " entries. trackLocations=" + trackLocations);
        }
        return Collections.unmodifiableList(result);
    }

    public Map<Activity, Double> getActivityStartTimes(RoundTrip<ActivityNode> roundTrip) {
        Map<Activity, Double> result = activityStartTimesPerRT.get(roundTrip.getIndex());
        if (result == null) {
            throw new IllegalStateException(
                "No activity start times data for RoundTrip index " + roundTrip.getIndex() + 
                ". Summary has " + activityStartTimesPerRT.size() + 
                " entries. trackActivityStartTimes=" + trackActivityStartTimes);
        }
        return Collections.unmodifiableMap(result);
    }

    public Map<Activity, List<Tuple<Double, Double>>> getActivityIntervals(RoundTrip<ActivityNode> roundTrip) {
        Map<Activity, List<Tuple<Double, Double>>> result = activityIntervalsPerRT.get(roundTrip.getIndex());
        if (result == null) {
            throw new IllegalStateException(
                "No activity intervals data for RoundTrip index " + roundTrip.getIndex() + 
                ". Summary has " + activityIntervalsPerRT.size() + 
                " entries. trackActivityIntervals=" + trackActivityIntervals);
        }
        return Collections.unmodifiableMap(result);
    }
}
