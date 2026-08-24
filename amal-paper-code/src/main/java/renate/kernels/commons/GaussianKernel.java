package renate.kernels.commons;

public class GaussianKernel {
    private double observedValue;
    private double lambda;
    private double invHalvLambda;

    GaussianKernel(double obs, double lambda) {
        this.observedValue = obs;
        this.lambda = lambda;
        this.invHalvLambda = 0.5 * lambda;
    }

    public double compute(double x) {
        double z = x * invHalvLambda;
        return Math.exp(- z * z);
    }
}
