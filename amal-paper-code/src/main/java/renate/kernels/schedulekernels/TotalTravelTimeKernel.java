package renate.kernels.schedulekernels;

import java.util.Map;

import renate.kernels.commons.ScalarGaussianKernel;
import renate.kernels.commons.Features;
import renate.kernels.commons.KernelSummary;
import renate.model.Person;
import renate.model.nodes.NodeWithActivity;
import se.vti.roundtrips.simulator.MoveEpisode;
import se.vti.roundtrips.single.RoundTrip;
import se.vti.utils.misc.Tuple;

public class TotalTravelTimeKernel extends ScalarGaussianKernel<TotalTravelTimeKernel.TttFeatures> implements ScheduleKernelBase {
    static class TttFeatures implements Features {
        private final double ttt; 
        public TttFeatures(RoundTrip<NodeWithActivity> rt) {
            double val = 0;
            for (int ii = 1; ii < rt.getEpisodes().size(); ii+=2) {
                var me = (MoveEpisode<NodeWithActivity>) rt.getEpisodes().get(ii);
                val += me.getDuration_h();
            }
            ttt = val;
        }
        public TttFeatures(Person person) {
            this.ttt = person.getTrip().getTotalTravelTime_h();
        }
        public double getTtt() { return this.ttt; }
    }

    private final double obsTtt;
    private Map<Tuple<String, String>, Double> pairwiseUncertainties;

    public TotalTravelTimeKernel setPairwiseUncertainties(Map<Tuple<String, String>, Double> uncertainties) {
        this.pairwiseUncertainties = uncertainties;
        return this;
    }

    double getCumulativeZoneUncertainty(RoundTrip<NodeWithActivity> rt, KernelSummary summary) {
        double cumulativeZoneSizes = 0.0;
        for (int ii = 0; ii < rt.size(); ii++) {
            String l1 = rt.getNode(ii).getBasicName();
            String l2 = rt.getNode((ii + 1) % rt.size()).getBasicName();
            cumulativeZoneSizes += pairwiseUncertainties.get(new Tuple(l1, l2));
        }
        return cumulativeZoneSizes;
    }

    public TotalTravelTimeKernel(double ttt, double lambda) {
        super(lambda);
        this.obsTtt = ttt;
    }

    @Override
    public double compute(TotalTravelTimeKernel.TttFeatures features) {
        throw new UnsupportedOperationException("Not supported here. Use compute(rt, summary) instead!");
    }

    @Override
    public double compute(RoundTrip<NodeWithActivity> rt, KernelSummary summary) {
        double diff = obsTtt - summary.getTtt(rt);
        double var = lambda2;
        if (pairwiseUncertainties != null) {
             var += getCumulativeZoneUncertainty(rt, summary);
        }
        return Math.exp(-0.5 * diff * diff / var);
    }

    @Override
    public void configureRequiredSummary(KernelSummary summary) {
        summary.setTrackTtt(true);
    }

    @Override
    public TotalTravelTimeKernel.TttFeatures extractFeatures(RoundTrip<NodeWithActivity> rt) {
        throw new UnsupportedOperationException("Unimplemented method 'extractFeatures'");
    }

    @Override
    public TotalTravelTimeKernel.TttFeatures extractFeatures(Person p) {
        throw new UnsupportedOperationException("Unimplemented method 'extractFeatures'");
    }

}
