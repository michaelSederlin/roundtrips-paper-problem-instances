package disaggregate.weights;

import java.util.List;
import java.util.Map;

import common.Activity;
import disaggregate.model.ActivityNode;
import se.vti.roundtrips.single.RoundTrip;
import se.vti.utils.misc.Tuple;

/**
 * Per-round-trip inputs shared by every response kernel evaluated against that round trip.
 *
 * Built once per proposal in ExplainRoundTripsByResponses and handed to each kernel, so the
 * interval extraction runs once per round trip rather than once per (round trip, response).
 * Activity intervals are computed lazily: when no schedule-overlap kernel is configured,
 * nothing is extracted at all.
 */
public final class KernelFeatures {

    private final RoundTrip<ActivityNode> roundTrip;
    private final Map<Activity, ActivityNode> accruingNodes;
    private final double horizon_h;

    private Map<Activity, List<Tuple<Double, Double>>> activityIntervals;

    public KernelFeatures(RoundTrip<ActivityNode> roundTrip, Map<Activity, ActivityNode> accruingNodes,
            double horizon_h) {
        this.roundTrip = roundTrip;
        this.accruingNodes = accruingNodes;
        this.horizon_h = horizon_h;
    }

    public RoundTrip<ActivityNode> roundTrip() {
        return this.roundTrip;
    }

    public int nStops() {
        return this.roundTrip.size();
    }

    public Map<Activity, List<Tuple<Double, Double>>> activityIntervals() {
        if (this.activityIntervals == null) {
            this.activityIntervals = ActivityIntervals.from(this.roundTrip, this.horizon_h, this.accruingNodes);
        }
        return this.activityIntervals;
    }
}
