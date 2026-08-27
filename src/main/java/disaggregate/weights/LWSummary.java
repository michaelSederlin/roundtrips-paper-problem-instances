package disaggregate.weights;


import se.vti.roundtrips.multiple.MultiRoundTripSummary;
import se.vti.roundtrips.single.RoundTrip;

import java.util.IdentityHashMap;
import java.util.Map;

import common.GridNodeWithActivity;

abstract class LWSummary implements MultiRoundTripSummary<GridNodeWithActivity> {
    private double totalLW = 0.0;
    private final Map<RoundTrip<GridNodeWithActivity>, Double> rtLWCache;

    LWSummary() {
        this.rtLWCache = new IdentityHashMap<>();
    }

    LWSummary(LWSummary parent) {
        this.totalLW = parent.totalLW;
        this.rtLWCache = new IdentityHashMap<>(parent.rtLWCache);
    }

    public double getTotalLW() { return totalLW; }

    @Override
    public void clear() {
        totalLW = 0.0;
        rtLWCache.clear();
    }

    protected abstract double roundTrip2LW(RoundTrip<GridNodeWithActivity> rt);

    @Override
    public abstract LWSummary clone();

    @Override
    public void update(int roundTripIndex, RoundTrip<GridNodeWithActivity> oldRT, RoundTrip<GridNodeWithActivity> newRT) {
        double oldLW; 
        if (oldRT == null) {
            oldLW = 0.0;
        } else {
            Double val = rtLWCache.remove(oldRT);
            oldLW = (val != null) ? val : roundTrip2LW(oldRT);
        }

        double newLW;
        if (newRT == null) {
            newLW = 0.0;
        } else {
            newLW = roundTrip2LW(newRT);
            rtLWCache.put(newRT, newLW);
        }

        totalLW = totalLW - oldLW + newLW;
    }
}
