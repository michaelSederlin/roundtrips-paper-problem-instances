package disaggregate.model.weights;

import disaggregate.model.Activity;
import disaggregate.model.ActivityNode;
import disaggregate.utils.summaries.UniqueActivityNodeSummary;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.simulator.StayEpisode;
import se.vti.roundtrips.single.RoundTrip;

import java.util.Map;

public class StayingSummary extends LWSummary {
    // private final Map<Integer, Double> stayDurationPerRT;
    private final Map<ActivityNode, Double> logSigma;
    private final boolean withDurationWeighting; 
    private final double epsilon; 
    private UniqueActivityNodeSummary uniqueActivityNodeSummary;

    private boolean geometricFlag;
    private double c = 0.0;
    private double exponent = 1.0;

    public void enableGeometric() {
        enableGeometric(1.0);
    }
    public void enableGeometric(double exponent) {
        if (c != 0) throw new IllegalArgumentException("Trying to set geometricFlag but c is not 0");
        this.exponent = exponent;
        this.geometricFlag = true;
    }
    public void setC(double cVal) { 
        if (geometricFlag && cVal != 0.0) throw new IllegalArgumentException("Trying to set c != 0 but geometric flag is not false");
        this.c = cVal;
    }

    public StayingSummary(Map<ActivityNode, Double> logSigma, boolean withDurationWeighting, double epsilon) {
        if (withDurationWeighting) {
            throw new UnsupportedOperationException("Duration weighting is not supported yet in StayingSummary");
        }

        this.logSigma = logSigma;
        this.withDurationWeighting = withDurationWeighting;
        this.epsilon = epsilon;
    }

    private StayingSummary(StayingSummary parent) {
        super(parent);
        this.logSigma = parent.logSigma;
        this.withDurationWeighting = parent.withDurationWeighting;
        this.epsilon = parent.epsilon;
        this.geometricFlag = parent.geometricFlag;
        this.c = parent.c;
        this.exponent = parent.exponent;
        // uniqueActivityNodeSummary is deliberately not copied: it is rebound from the
        // containing MultiRoundTrip on every update, so a clone resolves its own.
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
            // exponent and c are mutually exclusive by construction (see enableGeometric/setC),
            // so this reduces to exponent*ls on the geometric path and to ls+c on the other.
            lw += exponent * ls + c;
            countedNodes ++;
        }

        if (geometricFlag) {
            return (countedNodes > 0) ? lw / countedNodes : 0.0;
        }
        return lw;
    }

    @Override
    public StayingSummary clone() {
        return new StayingSummary(this);
    }
}
