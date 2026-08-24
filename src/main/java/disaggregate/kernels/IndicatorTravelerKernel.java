package disaggregate.kernels;

import disaggregate.model.Person;
import disaggregate.model.nodes.NodeWithActivity;
import se.vti.roundtrips.single.RoundTrip;

public class IndicatorTravelerKernel extends TravelerKernel{
    private final double matchingValue;
    private final double mismatchValue;
    private double tolerance = 1e-5;

    public IndicatorTravelerKernel(Person observedPerson) {
        super(observedPerson);
        matchingValue = 1.0;
        mismatchValue = 0.0;
    }

    public IndicatorTravelerKernel(Person observedPerson, double matchingValue, double mismatchValue) {
        super(observedPerson);
        this.matchingValue = matchingValue;
        this.mismatchValue = mismatchValue;
    }

    public IndicatorTravelerKernel withTolerance(double val) {
        this.tolerance = val;
        return this;
    }

    public boolean withinTolerance(double val1, double val2) {
        return Math.abs(val1 - val2) <= tolerance;
    }

    @Override
    public double compute(RoundTrip<NodeWithActivity> rt, KernelSummary summary) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'compute'");
    }

    @Override
    public double compute(TravelerKernel.Features agent) {
        if (!withinTolerance(observations.age(), agent.age())) return mismatchValue;
        if (!withinTolerance(observations.income(), agent.income())) return mismatchValue;
        if (!observations.gender().equals(agent.gender())) return mismatchValue;
        return matchingValue;
    }

    @Override
    public double[] getParameters() { return new double[0]; }
    @Override
    public void setParameters(double[] p) { }

    @Override
    public Features extractFeatures(RoundTrip<NodeWithActivity> rt) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'extractFeatures'");
    }

    @Override
    public Features extractFeatures(Person p) {
        return super.extractFeatures(p);
    }

    @Override
    public void configureRequiredSummary(KernelSummary summary) { }
    
}
