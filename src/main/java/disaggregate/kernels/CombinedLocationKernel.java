package disaggregate.kernels;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import disaggregate.kernels.KernelSummary;

import disaggregate.model.Activity;
import disaggregate.model.Person;
import disaggregate.model.nodes.ActivityNode;
import disaggregate.model.nodes.NodeWithActivity;
import se.vti.roundtrips.single.RoundTrip;

public class CombinedLocationKernel extends LocationKernel<CombinedLocationKernel.CombinedFeatures> {
    static class CombinedFeatures extends LocationKernel.LocationFeatures {
        private final List<LocationKernel.LocationFeatures> subFeatures;

        public CombinedFeatures(List<LocationKernel.LocationFeatures> subFeatures) {
            super(List.of()); // Not used
            this.subFeatures = subFeatures;
        }

        public List<LocationKernel.LocationFeatures> getSubFeatures() {
            return subFeatures;
        }

        public LocationKernel.LocationFeatures getSubFeature(int idx) {
            return subFeatures.get(idx);
        }
    }

    private final List<LocationKernel<? extends LocationKernel.LocationFeatures>> kernels;
    private double[] weights; 

    public CombinedLocationKernel(LocationKernel<? extends LocationKernel.LocationFeatures>... kernels) {
        super(null);
        this.kernels = List.of(kernels);
        this.weights = new double[kernels.length];
        Arrays.fill(this.weights, 1.0);
    }
    
    public CombinedLocationKernel setMixtureWeights(double[] weights) {
        if (weights.length != this.kernels.size()) {
            throw new IllegalArgumentException("Weights must be the same length as the number of kernels");
        }
        this.weights = weights;

        return this;
    }

    @Override
    public CombinedFeatures extractFeatures(Person p) {
        List<LocationKernel.LocationFeatures> features = new ArrayList<>();
        for (LocationKernel<? extends LocationKernel.LocationFeatures> kernel: kernels) {
            features.add((LocationKernel.LocationFeatures) kernel.extractFeatures(p));
        }
        return new CombinedFeatures(features);
    }

    @Override
    public CombinedFeatures extractFeatures(RoundTrip<NodeWithActivity> rt) {
        List<LocationKernel.LocationFeatures> features = new ArrayList<>(); 
        for (LocationKernel<? extends LocationKernel.LocationFeatures> k : kernels) {
            features.add((LocationKernel.LocationFeatures) k.extractFeatures(rt));
        }

        return new CombinedFeatures(features);
    }

    @Override
    public double compute(CombinedFeatures features) {
        double w = 1.0;
        for (int ii = 0; ii < kernels.size(); ii++) {
            double kw = computeKernel(kernels.get(ii), features.getSubFeature(ii));
            w *= (w == 1.0) ? kw : Math.pow(kw, weights[ii]);
        }
        return w;
    }

    @Override
    public double compute(RoundTrip<NodeWithActivity> rt, KernelSummary summary) {
        double w = 1.0;
        for (int ii = 0; ii < kernels.size(); ii++) {
            double kw = kernels.get(ii).compute(rt, summary);
            w *= (w == 1.0) ? kw : Math.pow(kw, weights[ii]);
        }
        return w;
    }

    private <F extends LocationKernel.LocationFeatures> double computeKernel(
        LocationKernel<F> kernel, 
        LocationKernel.LocationFeatures features) {
            return kernel.compute((F) features); 
        }

    @Override
    public void configureRequiredSummary(KernelSummary summary) {
        for (LocationKernel<? extends LocationKernel.LocationFeatures> kernel : kernels) {
            kernel.configureRequiredSummary(summary);
        }
    }
    public List<LocationKernel<? extends LocationKernel.LocationFeatures>> getKernels() {
        return kernels;
    }
}
