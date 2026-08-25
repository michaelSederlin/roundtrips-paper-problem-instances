package disaggregate.model.weights;

import disaggregate.model.Activity;
import disaggregate.model.ActivityNode;
import disaggregate.utils.summaries.UniqueActivityNodeSummary;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.simulator.StayEpisode;
import se.vti.roundtrips.single.RoundTrip;

import java.util.Map;

public class StayingSummary extends LWSummary {
    private final Map<ActivityNode, Double> logSigma;
    private final double epsilon; 
    private UniqueActivityNodeSummary uniqueActivityNodeSummary;

    public StayingSummary(Map<ActivityNode, Double> logSigma, boolean withDurationWeighting, double epsilon) {
        if (withDurationWeighting) {
            throw new UnsupportedOperationException("Duration weighting is not supported yet in StayingSummary");
        }
        this.logSigma = logSigma;
        this.epsilon = epsilon;
    }

    private StayingSummary(StayingSummary parent) {
        super(parent);
        this.logSigma = parent.logSigma;
        this.epsilon = parent.epsilon;
    }

    @Override 
    public void update(RoundTrip<ActivityNode> newRT, MultiRoundTrip<ActivityNode> mrt){
        this.uniqueActivityNodeSummary = mrt.getSummary(UniqueActivityNodeSummary.class);
        int idx = newRT.getIndex();
        update(idx, mrt.getRoundTrip(idx), newRT);
    }

    @Override
    protected double roundTrip2LW(RoundTrip<ActivityNode> rt) {
        if (rt == null) return 0.0;
        
        double lw = 0.0;

        Map<Activity, ActivityNode> accruingNodes = (uniqueActivityNodeSummary != null) 
            ? uniqueActivityNodeSummary.getRoundTripActivityNodes(rt.getIndex())
            : Map.of();

        int countedNodes = 0;

        for (int jj = 0; jj < rt.getEpisodes().size(); jj+=2) {
            ActivityNode loc = ((StayEpisode<ActivityNode>) rt.getEpisodes().get(jj)).getLocation();
            ActivityNode accruingNode = accruingNodes.get(loc.getActivity());
            if ((accruingNode != null) && !accruingNode.equals(loc)) continue;

            Double ls = logSigma.get(loc);
            if (ls == null) {
                throw new IllegalStateException("Log sigma not found for location: " + loc);
            }
            lw += ls;
            countedNodes ++;
        }
        return (countedNodes > 0) ? lw / countedNodes: 0.0;
    }

    @Override
    public StayingSummary clone() {
        return new StayingSummary(this);
    }
}
