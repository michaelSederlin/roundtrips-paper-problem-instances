package renate.kernels.commons;

public abstract class ScalarGaussianKernel<F extends Features> implements Kernel<F> {
    protected double lambda, lambda2, halfInvLambda;

    protected ScalarGaussianKernel(double lambda) { setParameters(new double[]{lambda}); }

    protected double gaussian(double diff) {
        return Math.exp(-halfInvLambda * diff * diff);
    }

    public double[] getParameters() {
        return new double[]{lambda};
    }

    @Override
    public void setParameters(double[] p) {
        this.lambda = p[0];
        this.lambda2 = this.lambda * this.lambda;
        this.halfInvLambda = 0.5 / lambda2;
    }
}
