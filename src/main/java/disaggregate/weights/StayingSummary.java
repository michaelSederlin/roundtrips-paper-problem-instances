package renate.model.weights;

import renate.model.Activity;
import renate.model.nodes.NodeWithActivity;
import renate.utils.summaries.UniqueActivityNodeSummary;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.simulator.StayEpisode;
import se.vti.roundtrips.single.RoundTrip;

import java.util.Map;

public class StayingSummary extends LWSummary {
    private final Map<NodeWithActivity, Double> logSigma;
    private final double epsilon; 
    private UniqueActivityNodeSummary uniqueActivityNodeSummary;

    private double exponent = 1.0;

    public StayingSummary(Map<NodeWithActivity, Double> logSigma, double epsilon) {
        this.logSigma = logSigma;
        this.epsilon = epsilon;
    }

    private StayingSummary(StayingSummary parent) {
        super(parent);
        this.logSigma = parent.logSigma;
        this.epsilon = parent.epsilon;
        this.exponent = parent.exponent;
    }

    @Override 
    public void update(RoundTrip<NodeWithActivity> newRT, MultiRoundTrip<NodeWithActivity> mrt){
        this.uniqueActivityNodeSummary = mrt.getSummary(UniqueActivityNodeSummary.class);
        int idx = newRT.getIndex();
        update(idx, mrt.getRoundTrip(idx), newRT);
    }

    @Override
    protected double roundTrip2LW(RoundTrip<NodeWithActivity> rt) {
        if (rt == null) return 0.0;
        
        double lw = 0.0;
        Map<Activity, NodeWithActivity> accruingNodes = (uniqueActivityNodeSummary != null) 
            ? uniqueActivityNodeSummary.getRoundTripActivityNodes(rt.getIndex())
            : Map.of();
        int countedNodes = 0;

        for (int jj = 0; jj < rt.getEpisodes().size(); jj+=2) {
            NodeWithActivity loc = ((StayEpisode<NodeWithActivity>) rt.getEpisodes().get(jj)).getLocation();
            NodeWithActivity accruingNode = accruingNodes.get(loc.getActivity());
            if ((accruingNode != null) && !accruingNode.equals(loc)) continue;

            Double ls = logSigma.get(loc);
            if (ls == null) {
                throw new IllegalStateException("Log sigma not found for location: " + loc);
            }
            lw += exponent * ls;
            countedNodes ++;
        }
        
        return lw / countedNodes;
    }

    @Override
    public StayingSummary clone() {
        return new StayingSummary(this);
    }
}
