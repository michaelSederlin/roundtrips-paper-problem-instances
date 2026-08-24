package renate.kernels.commons;

import renate.model.Person;
import renate.model.nodes.ActivityNode;
import renate.model.nodes.NodeWithActivity;
import se.vti.roundtrips.single.RoundTrip;

public interface Kernel<T> {
    default int getKernelIndex() { return -1; }

    double compute(RoundTrip<NodeWithActivity> rt, KernelSummary summary);
    public double compute(T val);

    double[] getParameters();
    void setParameters(double[] p);

    public void configureRequiredSummary(KernelSummary summary);

    default Double getCached(RoundTrip<NodeWithActivity> rt, KernelSummary summary) {
        if (getKernelIndex() < 0) return null;
        int rtIdx = rt.getIndex();
        int kIdx = getKernelIndex();
        return summary.isCacheValid(kIdx, rtIdx) ? summary.getKernelValue(kIdx, rtIdx) : null;
    }

    default void setCached(RoundTrip<NodeWithActivity> rt, KernelSummary summary, double val) {
        if (getKernelIndex() < 0) return;
        summary.setKernelValue(getKernelIndex(), rt.getIndex(), val);
    }

    default Kernel withParameters(double[] parameters) {
        setParameters(parameters);
        return this;
    }
}
