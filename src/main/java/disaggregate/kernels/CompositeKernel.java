package disaggregate.kernels;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import disaggregate.model.Person;
import disaggregate.model.ActivityNode;
import se.vti.roundtrips.single.RoundTrip;

public class CompositeKernel implements Kernel<CompositeKernel.CompositeFeatures> {
    private int kernelIndex = -1;
    private final List<Kernel<? extends Features>> kernels = new ArrayList<>(); 
    private final List<Double> kernelWeights = new ArrayList<>(); 
    private final List<Role> kernelRoles = new ArrayList<>();

    public static class CompositeFeatures implements Features {
        private final List<Features> subFeatures;

        public CompositeFeatures(List<Features> subFeatures) {
            this.subFeatures = subFeatures;
        }

        public Features getSubFeature(int idx) {
            return subFeatures.get(idx);
        }
    }

    @SafeVarargs
    public CompositeKernel(Kernel<? extends Features> ... kernels) {
        for (Kernel<? extends Features> k : kernels) add(k, null, 1.0);
    }

    public int getKernelIndex() {
        return kernelIndex;
    }

    public CompositeKernel add(Kernel<? extends Features> k, Role role, double weight) { 
        kernels.add(k);
        kernelRoles.add(role);
        kernelWeights.add(weight);
        return this;
    }
    
    public CompositeKernel add(Kernel<? extends Features> k, double weight) { return add(k, null, weight); }
    public CompositeKernel add(Kernel<? extends Features> k) { return add(k, null, 1.0); }
    public CompositeKernel add(Kernel<? extends Features> k, Role role) { return add(k, role, 1.0); }

    public CompositeKernel setMixtureWeights(double[] weights) {
        if (weights.length != this.kernels.size()) {
            throw new IllegalArgumentException("Length of weights must match number of kernels");
        }
        kernelWeights.clear();
        for (double w : weights) kernelWeights.add(w);
        return this;
    }

    @Override 
    public double compute(RoundTrip<ActivityNode> rt, KernelSummary summary) {
        Double cached = getCached(rt, summary);
        if (cached != null) return cached;

        double combinedWeight = 1.0; 
        for (int ii = 0; ii < kernels.size(); ii++) {
            double weight = kernelWeights.get(ii);
            double kw = kernels.get(ii).compute(rt, summary); 
            combinedWeight *= (weight == 1) ? kw : Math.pow(kw, weight);
        }
        setCached(rt, summary, combinedWeight);
        return combinedWeight; 
    }

    @Override
    public double compute(CompositeFeatures features) {
        double cw = 1.0; 
        for (int ii = 0; ii < kernels.size(); ii++) {
            double weight = kernelWeights.get(ii);
            Kernel<Features> k = (Kernel<Features>) kernels.get(ii); 
            double kw = k.compute(features.getSubFeature(ii)); 
            cw *= (weight == 1.0) ? kw : Math.pow(kw, weight); 
        }

        return cw; 
    }

    public final double computeExcludingRoles(RoundTrip<ActivityNode> rt, KernelSummary summary, Role... roles) {
        double combinedWeight = 1.0;
        List<Role> exclusionList = List.of(roles);

        for (int ii = 0; ii < kernels.size(); ii++) {
            if (exclusionList.contains(kernelRoles.get(ii))) continue;

            double kw = kernels.get(ii).compute(rt, summary);
            combinedWeight *= Math.pow(kw, kernelWeights.get(ii));
        }
        return combinedWeight;
    }    

    @SafeVarargs
    public final double computeExcludingKernelClasses(RoundTrip<ActivityNode> rt, KernelSummary summary, Class<? extends Kernel> ... kernelClasses) {
        double combinedWeight = 1.0;
        List<Class<? extends Kernel>> exclusionList = List.of(kernelClasses);

        for (int ii = 0; ii < kernels.size(); ii++) {
            Kernel<?> kernel = kernels.get(ii);
            if (exclusionList.stream().anyMatch(ex -> ex.isInstance(kernel))) continue;

            double kw = kernel.compute(rt, summary);
            combinedWeight *= Math.pow(kw, kernelWeights.get(ii));
        }
        return combinedWeight;
    }
    @Override 
    public void configureRequiredSummary(KernelSummary summary) {
        this.kernelIndex = summary.registerKernel();
        for (Kernel<? extends Features> kernel : kernels) {
            kernel.configureRequiredSummary(summary);
        }
    }

    public List<Kernel<? extends Features>> getKernels() { return this.kernels; }
    public Kernel<? extends Features> getKernel(int idx) {
        return this.kernels.get(idx); 
    }

    public <T> T getKernel(Class<T> kernelClass) {
        for (Kernel<?> k : kernels) {
            if (kernelClass.isInstance(k)) {
                return kernelClass.cast(k);
            }
        }
        return null; 
    }

    public <T extends Kernel<?>> T getKernel(Role role) {
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

    @Override
    public CompositeFeatures extractFeatures(RoundTrip<ActivityNode> rt) {
        throw new UnsupportedOperationException("Unimplemented method 'extractFeatures'");
    }

    @Override
    public CompositeFeatures extractFeatures(Person p) {
        throw new UnsupportedOperationException("Unimplemented method 'extractFeatures'");
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