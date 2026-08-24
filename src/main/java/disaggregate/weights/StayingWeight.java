package renate.model.weights;

import java.util.Map;
import java.util.HashMap;

import renate.model.Activity;
import renate.model.nodes.NodeWithActivity;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.multiple.MultiRoundTripSummary;
import se.vti.roundtrips.simulator.StayEpisode;
import se.vti.roundtrips.single.RoundTrip;
import se.vti.utils.misc.metropolishastings.MHWeight;

//*
// Wrapper that ensures we compute the geometric mean of the staying weights. 
// Trial to mitigate the bias
//  */

public class GeometricMeanStayingWeight implements MHWeight<MultiRoundTrip<NodeWithActivity>>{
    private double exponent = 1.0;
    private final double epsilon; 
    private final Map<NodeWithActivity, Double> nodeActivityOpportunities;
    private final Map<RoundTrip<NodeWithActivity>, Double> roundTripLogWeightsCache;
    private final Map<NodeWithActivity, Double> logSigma;

    public GeometricMeanStayingWeight withExponent(double exponent) {
        this.exponent = exponent;
        return this;
    }

    public GeometricMeanStayingWeight(Map<NodeWithActivity, Double> nodeActivityOpportunities) {    
        this(nodeActivityOpportunities, 1e-6);
    }

    public GeometricMeanStayingWeight(Map<NodeWithActivity, Double> nodeActivityOpportunities, double epsilon) {
        this.epsilon = epsilon;
        this.nodeActivityOpportunities = nodeActivityOpportunities;
        this.roundTripLogWeightsCache = new HashMap<>();
        this.logSigma = new HashMap<>();
        for (Map.Entry<NodeWithActivity, Double> entry : nodeActivityOpportunities.entrySet()) {
            logSigma.put(entry.getKey(), Math.log(entry.getValue() + epsilon));
        }
    }

    public double logWeight(MultiRoundTrip<NodeWithActivity> state, MultiRoundTripSummary<NodeWithActivity> summary) {
        // Why do I have this?
        throw new UnsupportedOperationException("Unimplemented method 'logWeight' with summary");
    }

    public void configureRequiredSummary(MultiRoundTrip<NodeWithActivity> mrt) {
        var staySummary = new StayingSummary(logSigma, false, this.epsilon);
        staySummary.enableGeometric(exponent);
        mrt.addSummary(staySummary);
        mrt.recomputeSummaries();
    }

    @Override
    public double logWeight(MultiRoundTrip<NodeWithActivity> state) {
        return state.getSummary(StayingSummary.class).getTotalLW();
    }
}
