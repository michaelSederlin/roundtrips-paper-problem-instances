package disaggregate.weights;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CompositeKernel implements Kernel {
    private final List<Kernel> kernels = new ArrayList<>(); 
    private final List<Double> kernelWeights = new ArrayList<>(); 
    private final List<Role> kernelRoles = new ArrayList<>();

    public CompositeKernel(Kernel ... kernels) {
        for (Kernel k : kernels) add(k, null, 1.0);
    }

    public CompositeKernel add(Kernel k, Role role, double weight) { 
        kernels.add(k);
        kernelRoles.add(role);
        kernelWeights.add(weight);
        return this;
    }
    
    public CompositeKernel add(Kernel k, double weight) { return add(k, null, weight); }
    public CompositeKernel add(Kernel k) { return add(k, null, 1.0); }
    public CompositeKernel add(Kernel k, Role role) { return add(k, role, 1.0); }

    public CompositeKernel setMixtureWeights(double[] weights) {
        if (weights.length != this.kernels.size()) {
            throw new IllegalArgumentException("Length of weights must match number of kernels");
        }
        kernelWeights.clear();
        for (double w : weights) kernelWeights.add(w);
        return this;
    }

    @Override 
    public double compute(KernelFeatures features) {
        double combinedWeight = 1.0; 
        for (int ii = 0; ii < kernels.size(); ii++) {
            double weight = kernelWeights.get(ii);
            double kw = kernels.get(ii).compute(features); 
            combinedWeight *= (weight == 1) ? kw : Math.pow(kw, weight);
        }
        return combinedWeight; 
    }

    public List<Kernel> getKernels() { return this.kernels; }
    public Kernel getKernel(int idx) {
        return this.kernels.get(idx); 
    }

    public <T> T getKernel(Class<T> kernelClass) {
        for (Kernel k : kernels) {
            if (kernelClass.isInstance(k)) {
                return kernelClass.cast(k);
            }
        }
        return null; 
    }

    public <T extends Kernel> T getKernel(Role role) {
        for (int ii = 0; ii < kernels.size(); ii++) {
            if (role.equals(kernelRoles.get(ii))) return (T) kernels.get(ii);
        }
        return null;
    }

    @Override
    public double[] getParameters() {
        throw new UnsupportedOperationException("Cannot get parameters from the composite kernel. Use method in subkernels instead!");
    }

    @Override
    public void setParameters(double[] params) {
        throw new UnsupportedOperationException("Cannot set parameters in the composite kernel. Use method in subkernels instead!");
    }

    public Set<Kernel> getComponentKernels() {
        Set<Kernel> kernels = new HashSet<>();
        for (Kernel k : this.getKernels()) {
            if (k instanceof CompositeKernel subck) {
                kernels.addAll(subck.getComponentKernels());
            }
                kernels.add(k);
        }
        return kernels;
    }
}
