package disaggregate.weights;

import java.util.Map;
import java.util.HashMap;

import disaggregate.utils.UniqueNodeSummary;
import common.Activity;
import common.GridNodeWithActivity;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.multiple.MultiRoundTripSummary;
import se.vti.roundtrips.simulator.StayEpisode;
import se.vti.roundtrips.single.RoundTrip;
import se.vti.utils.misc.metropolishastings.MHWeight;

//*
// Wrapper that ensures we compute the geometric mean of the staying weights. 
// Trial to mitigate the bias
//  */

public class StayingWeight implements MHWeight<MultiRoundTrip<GridNodeWithActivity>>{
    private final double epsilon; 
    private final Map<GridNodeWithActivity, Double> nodeActivityOpportunities;
    private final Map<RoundTrip<GridNodeWithActivity>, Double> roundTripLogWeightsCache;
    private final Map<GridNodeWithActivity, Double> logSigma;


    public StayingWeight(Map<GridNodeWithActivity, Double> nodeActivityOpportunities) {    
        this(nodeActivityOpportunities, 1e-6);
    }

    public StayingWeight(Map<GridNodeWithActivity, Double> nodeActivityOpportunities, double epsilon) {
        this.epsilon = epsilon;
        this.nodeActivityOpportunities = nodeActivityOpportunities;
        this.roundTripLogWeightsCache = new HashMap<>();
        this.logSigma = new HashMap<>();
        for (Map.Entry<GridNodeWithActivity, Double> entry : nodeActivityOpportunities.entrySet()) {
            logSigma.put(entry.getKey(), Math.log(entry.getValue() + epsilon));
        }
    }

    public double logWeight(MultiRoundTrip<GridNodeWithActivity> state, MultiRoundTripSummary<GridNodeWithActivity> summary) {
        // Why do I have this?
        throw new UnsupportedOperationException("Unimplemented method 'logWeight' with summary");
    }

    public void configureRequiredSummary(MultiRoundTrip<GridNodeWithActivity> mrt) {
        var staySummary = new StayingSummary(this.epsilon);
        mrt.addSummary(staySummary);
        mrt.recomputeSummaries();
    }

    @Override
    public double logWeight(MultiRoundTrip<GridNodeWithActivity> state) {
        return state.getSummary(StayingSummary.class).getTotalLW();
    }

class StayingSummary extends LWSummary {
    // private final Map<GridNodeWithActivity, Double> logSigma;
    private final double epsilon; 
    private UniqueNodeSummary uniqueNodeSummary;

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
    public void update(RoundTrip<GridNodeWithActivity> newRT, MultiRoundTrip<GridNodeWithActivity> mrt){
        this.uniqueNodeSummary = mrt.getSummary(UniqueNodeSummary.class);
        int idx = newRT.getIndex();
        update(idx, mrt.getRoundTrip(idx), newRT);
    }

    @Override
    protected double roundTrip2LW(RoundTrip<GridNodeWithActivity> rt) {
        if (rt == null) return 0.0;
        
        double lw = 0.0;

        Map<Activity, GridNodeWithActivity> accruingNodes = (uniqueNodeSummary != null) 
            ? uniqueNodeSummary.getRoundTripNodes(rt.getIndex())
            : Map.of();

        int countedNodes = 0;

        for (int jj = 0; jj < rt.getEpisodes().size(); jj+=2) {
            GridNodeWithActivity loc = ((StayEpisode<GridNodeWithActivity>) rt.getEpisodes().get(jj)).getLocation();
            GridNodeWithActivity accruingNode = accruingNodes.get(loc.getActivity());
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
