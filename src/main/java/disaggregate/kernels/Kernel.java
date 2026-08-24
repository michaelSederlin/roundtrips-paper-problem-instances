package disaggregate.kernels;

import disaggregate.model.Person;
import disaggregate.model.ActivityNode;
import disaggregate.model.ActivityNode;
import se.vti.roundtrips.single.RoundTrip;

public interface Kernel<T> {
    default int getKernelIndex() { return -1; }

    double compute(RoundTrip<ActivityNode> rt, KernelSummary summary);
    public double compute(T val);

    double[] getParameters();
    void setParameters(double[] p);

    T extractFeatures(RoundTrip<ActivityNode> rt);
    T extractFeatures(Person p);

    public void configureRequiredSummary(KernelSummary summary);

    default Double getCached(RoundTrip<ActivityNode> rt, KernelSummary summary) {
        if (getKernelIndex() < 0) return null;
        int rtIdx = rt.getIndex();
        int kIdx = getKernelIndex();
        return summary.isCacheValid(kIdx, rtIdx) ? summary.getKernelValue(kIdx, rtIdx) : null;
//        return null;
//        return summary.getKernelValue(this, rt.getIndex());
    }

    default void setCached(RoundTrip<ActivityNode> rt, KernelSummary summary, double val) {
        if (getKernelIndex() < 0) return;
        summary.setKernelValue(getKernelIndex(), rt.getIndex(), val);
    }

    default Kernel withParameters(double[] parameters) {
        setParameters(parameters);
        return this;
    }
}
