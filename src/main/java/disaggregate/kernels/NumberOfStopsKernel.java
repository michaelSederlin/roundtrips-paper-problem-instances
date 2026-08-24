package disaggregate.kernels;

import java.util.Map;

import disaggregate.model.ActivityNode;
import se.vti.roundtrips.single.RoundTrip;
import disaggregate.model.Activity;
import disaggregate.model.Person;


public class NumberOfStopsKernel extends ScalarGaussianKernel<NumberOfStopsKernel.NumStopsFeatures> implements ScheduleKernelBase {

    static class NumStopsFeatures implements Features {
        private final int numStops;
        public NumStopsFeatures(RoundTrip<ActivityNode> rt) { this.numStops = rt.size(); }
        public NumStopsFeatures(Person person) { this.numStops = person.getTrip().size(); }
        public int getNumStops() { return numStops; }
    }

    private final int observedNumStops;

    public NumberOfStopsKernel(Person person) { this(person.getTrip().size()); }
    public NumberOfStopsKernel(Person person, double lambda) { this(person.getTrip().size(), lambda); }
    public NumberOfStopsKernel(int observedNumStops) { this(observedNumStops, 1.0); }
    public NumberOfStopsKernel(int observedNumStops, double lambda) {
        super(lambda);
        this.observedNumStops = observedNumStops;
    }

    @Override
    public double compute(NumberOfStopsKernel.NumStopsFeatures feats) {
        throw new UnsupportedOperationException("Not implemented. Use compute(RoundTrip, KernelSummary) instead.");
        // return GaussianKernel.compute(observedNumStops - feats.getNumStops(), lambda);
    }

    @Override
    public double compute(RoundTrip<ActivityNode> rt, KernelSummary summary) {
    //    Double cached = getCached(rt, summary);  // caching actually makes performance worse for this kernel function.
        // if (cached != null) return cached;
        double diff = observedNumStops - summary.getNStops(rt);
        return gaussian(diff);
        // double result = GaussianKernel.compute(observedNumStops - summary.getNStops(rt), lambda);
        // setCached(rt, summary, result);
        // return result;
    }

    @Override
    public void configureRequiredSummary(KernelSummary summary) {
        summary.setTrackNumStops(true);
    }

    @Override
    public NumberOfStopsKernel.NumStopsFeatures extractFeatures(RoundTrip<ActivityNode> rt) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'extractFeatures'");
    }

    @Override
    public NumberOfStopsKernel.NumStopsFeatures extractFeatures(Person p) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'extractFeatures'");
    }
}