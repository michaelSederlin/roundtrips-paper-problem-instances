package disaggregate.weights;

public interface Kernel {

    double compute(KernelFeatures features);

    double[] getParameters();
    void setParameters(double[] p);

    default Kernel withParameters(double[] parameters) {
        setParameters(parameters);
        return this;
    }
}
