package disaggregate.kernels;

import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import disaggregate.model.Activity;
import disaggregate.model.ActivityNode;
import disaggregate.utils.summaries.UniqueActivityNodeSummary;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.multiple.MultiRoundTripSummary;
import se.vti.roundtrips.single.RoundTrip;
import se.vti.utils.misc.Tuple;

public class KernelSummary implements MultiRoundTripSummary<ActivityNode> {
    private static final Logger logger = LogManager.getLogger(KernelSummary.class);
    
    private boolean trackActivityIntervals = false; 
    private boolean trackNumStops = false;

    private double[][] kernelCache;
    private boolean[][] kernelCacheValid;
    private int nextKernelIndex = 0;
    
    private final Map<Integer, Integer> nStopsPerRT;
    private final Map<Integer, Map<Activity, List<Tuple<Double, Double>>>> activityIntervalsPerRT; 

    private final int populationSize; 
    private final boolean[][] excludedPairs;

    private UniqueActivityNodeSummary uniqueActivityNodesSummary;
    private double totalLW;

    private KernelSummary(int populationSize, boolean[][] excludedPairs, boolean skipInit) {
        this.excludedPairs = excludedPairs;
        this.populationSize = populationSize;
        this.totalLW = 0.0;
        this.nStopsPerRT = new HashMap<>(0);
        this.activityIntervalsPerRT = new HashMap<>(0);
    }

    public KernelSummary(int populationSize, boolean[][] excludedPairs) {
        this.excludedPairs = excludedPairs;
        this.populationSize = populationSize;
        this.totalLW = 0.0;

        this.nStopsPerRT = new HashMap<>(populationSize);
        this.activityIntervalsPerRT = new HashMap<>(populationSize);
    }

    public void setTrackActivityIntervals(boolean val) { this.trackActivityIntervals = val; }
    public void setTrackNumStops(boolean val) { this.trackNumStops = val; }

    public double getTotalLW() { return this.totalLW; }

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
    }

    public void setKernelValue(int kernelIndex, int rtIndex, double value) {
        if (kernelCache == null) return;
        kernelCache[kernelIndex][rtIndex] = value;
        kernelCacheValid[kernelIndex][rtIndex] = true;
    }


    @Override
    public void clear() {
        nStopsPerRT.clear();
        activityIntervalsPerRT.clear();
    }

    @Override
    public void update(RoundTrip<ActivityNode> newRoundTrip, MultiRoundTrip<ActivityNode> mrt) {
        this.uniqueActivityNodesSummary = mrt.getSummary(UniqueActivityNodeSummary.class);
        int idx = newRoundTrip.getIndex();
        update(idx, mrt.getRoundTrip(idx), newRoundTrip);
    }

    @Override 
    public void update(int idx, RoundTrip<ActivityNode> oldRoundTrip, RoundTrip<ActivityNode> newRoundTrip) {
        nStopsPerRT.remove(idx);
        activityIntervalsPerRT.remove(idx);

        if (kernelCacheValid != null) {
            for (int k = 0; k < nextKernelIndex; k++) {
                kernelCacheValid[k][idx] = false;
            }
        }

        if (newRoundTrip != null) {
            Map<Activity, List<Tuple<Double, Double>>> actIntervals = new EnumMap<>(Activity.class);
            
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

            if (trackNumStops) nStopsPerRT.put(idx, newRoundTrip.size());
            if (trackActivityIntervals) activityIntervalsPerRT.put(idx, actIntervals);
        }
    }

    @Override 
    public MultiRoundTripSummary<ActivityNode> clone() {
        final KernelSummary copy = new KernelSummary(this.populationSize, this.excludedPairs, true);
        
        copy.trackNumStops = this.trackNumStops;
        copy.trackActivityIntervals = this.trackActivityIntervals;
        copy.nStopsPerRT.putAll(this.nStopsPerRT);
        copy.activityIntervalsPerRT.putAll(this.activityIntervalsPerRT);

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
