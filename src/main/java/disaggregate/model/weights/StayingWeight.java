package disaggregate.model.weights;

import java.util.Map;
import java.util.HashMap;

import disaggregate.model.Activity;
import disaggregate.model.ActivityNode;
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
        var staySummary = new StayingSummary(logSigma, false, this.epsilon);
        mrt.addSummary(staySummary);
        mrt.recomputeSummaries();
    }

    @Override
    public double logWeight(MultiRoundTrip<ActivityNode> state) {
        return state.getSummary(StayingSummary.class).getTotalLW();
    }
}
