package disaggregate.weights;

import java.util.Map;
import java.util.HashMap;

import disaggregate.model.Activity;
import disaggregate.model.ActivityNode;
import disaggregate.utils.UniqueActivityNodeSummary;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.multiple.MultiRoundTripSummary;
import se.vti.roundtrips.simulator.StayEpisode;
import se.vti.roundtrips.single.RoundTrip;
import se.vti.utils.misc.metropolishastings.MHWeight;

//*
// Wrapper that ensures we compute the geometric mean of the staying weights. 
// Trial to mitigate the bias
//  */

public class StayingWeight implements MHWeight<MultiRoundTrip<ActivityNode>>{
    private double exponent = 1.0;
    private final double epsilon; 
    private final Map<ActivityNode, Double> nodeActivityOpportunities;
    private final Map<RoundTrip<ActivityNode>, Double> roundTripLogWeightsCache;
    private final Map<ActivityNode, Double> logSigma;

    public StayingWeight withExponent(double exponent) {
        this.exponent = exponent;
        return this;
    }

    public StayingWeight(Map<ActivityNode, Double> nodeActivityOpportunities) {    
        this(nodeActivityOpportunities, 1e-6);
    }

    public StayingWeight(Map<ActivityNode, Double> nodeActivityOpportunities, double epsilon) {
        this.epsilon = epsilon;
        this.nodeActivityOpportunities = nodeActivityOpportunities;
        this.roundTripLogWeightsCache = new HashMap<>();
        this.logSigma = new HashMap<>();
        for (Map.Entry<ActivityNode, Double> entry : nodeActivityOpportunities.entrySet()) {
            logSigma.put(entry.getKey(), Math.log(entry.getValue() + epsilon));
        }
    }

    public double logWeight(MultiRoundTrip<ActivityNode> state, MultiRoundTripSummary<ActivityNode> summary) {
        // Why do I have this?
        throw new UnsupportedOperationException("Unimplemented method 'logWeight' with summary");
    }

    public void configureRequiredSummary(MultiRoundTrip<ActivityNode> mrt) {
        var staySummary = new StayingSummary(this.epsilon);
        mrt.addSummary(staySummary);
        mrt.recomputeSummaries();
    }

    @Override
    public double logWeight(MultiRoundTrip<ActivityNode> state) {
        return state.getSummary(StayingSummary.class).getTotalLW();
    }

class StayingSummary extends LWSummary {
    // private final Map<ActivityNode, Double> logSigma;
    private final double epsilon; 
    private UniqueActivityNodeSummary uniqueActivityNodeSummary;

    public StayingSummary(double epsilon) {
        // this.logSigma = logSigma;
        this.epsilon = epsilon;
    }

    private StayingSummary(StayingSummary parent) {
        super(parent);
        // this.logSigma = parent.logSigma;
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
}
