package disaggregate.weights;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import disaggregate.model.Activity;
import disaggregate.model.Person;
import disaggregate.model.SurveyResponse;
import disaggregate.model.ActivityNode;
import disaggregate.utils.UniqueActivityNodeSummary;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.multiple.MultiRoundTripSummary;
import se.vti.roundtrips.single.RoundTrip;

public class ExplainRoundTripsByResponses implements ResponseWeight<ActivityNode>{
    private static final double HORIZON_H = 24.0;

    private final List<SurveyResponse<ActivityNode>> responses;
    private final List<Person> syntheticPopulation;
    private final ExplainSummary explainSummary;

    private final double[][] personResponseWeights;
    private double[][] lastScheduleKernels;
    private double[][] lastLocationKernel;

    private final boolean[][] excludedPairs;
    private double threshold = 0.0;

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public ExplainRoundTripsByResponses(List<SurveyResponse<ActivityNode>> responses, List<Person> syntheticPopulation) {
        this(responses, syntheticPopulation, 0.0);
    }

    public ExplainRoundTripsByResponses(List<SurveyResponse<ActivityNode>> responses, List<Person> syntheticPopulation, double threshold) {
        this.threshold = threshold;
        this.responses = responses;
        this.syntheticPopulation = syntheticPopulation;
        this.lastLocationKernel = new double[syntheticPopulation.size()][responses.size()];
        this.lastScheduleKernels = new double[syntheticPopulation.size()][responses.size()];
        this.excludedPairs = new boolean[syntheticPopulation.size()][responses.size()];

        this.personResponseWeights = new double[this.syntheticPopulation.size()][responses.size()];
        for (int ii = 0; ii < this.syntheticPopulation.size(); ii++) {
            double[] personWeights = this.personResponseWeights[ii];
            for (int jj = 0; jj < responses.size(); jj++) {
                CompositeKernel k = this.responses.get(jj).getKernel();
                
                Kernel tkRaw = k.getKernel(Role.TRAVELER);
                if (tkRaw != null && !(tkRaw instanceof TravelerKernel)) {
                    throw new IllegalStateException("TRAVELER Role must be a TravelerKernel, got " + tkRaw.getClass());
                }
                TravelerKernel tk = (TravelerKernel) tkRaw;
                personWeights[jj] = (tk != null)
                ? tk.compute(syntheticPopulation.get(ii))
                : 1.0;
                
                if (personWeights[jj] < threshold) {
                    this.excludedPairs[ii][jj] = true;
                } 
            }
        }

        this.explainSummary = new ExplainSummary(syntheticPopulation.size());
    }

    @Override
    public double logWeight(MultiRoundTrip<ActivityNode> multiRoundTrip) {
        return multiRoundTrip.getSummary(ExplainSummary.class).getTotalLW();
    }

    public void configureRequiredSummary(MultiRoundTrip<ActivityNode> mrt) {
        mrt.addSummary(explainSummary);
        mrt.recomputeSummaries();
    }

    class ExplainSummary implements MultiRoundTripSummary<ActivityNode> {
        private final double[] perAgentLogWeight;
        private double totalLW = 0.0;

        ExplainSummary(int nAgents) {
            this.perAgentLogWeight = new double[nAgents];
        }

        private ExplainSummary(ExplainSummary parent) {
            this.perAgentLogWeight = parent.perAgentLogWeight.clone();
            this.totalLW = parent.totalLW;
        }

        // Requires UniqueActivityNodeSummary to be registered first when the feature is in use --
        // summaries are updated in registration order. Absence means the feature is off.
        @Override
        public void update(RoundTrip<ActivityNode> newRT, MultiRoundTrip<ActivityNode> state) {
            int idx = newRT.getIndex();
            UniqueActivityNodeSummary uniq = state.getSummary(UniqueActivityNodeSummary.class);
            recompute(idx, newRT, (uniq != null) ? uniq.getRoundTripActivityNodes(idx) : Map.of());
        }

        // Unreachable: MultiRoundTrip only ever calls the two-argument update, which is
        // overridden above. Without the MultiRoundTrip there is no way to reach
        // UniqueActivityNodeSummary, so fail loudly rather than score against the wrong nodes.
        @Override
        public void update(int idx, RoundTrip<ActivityNode> oldRT, RoundTrip<ActivityNode> newRT) {
            throw new UnsupportedOperationException(
                    "ExplainSummary requires the MultiRoundTrip to resolve accruing activity nodes");
        }

        private void recompute(int idx, RoundTrip<ActivityNode> newRT, Map<Activity, ActivityNode> accruingNodes) {
            double personWeight = 0.0;
            if (newRT != null) {
                // Built once per round trip, so the interval extraction is shared by all responses.
                KernelFeatures features = new KernelFeatures(newRT, accruingNodes, HORIZON_H);
                for (int j = 0; j < responses.size(); j++) {
                    if (excludedPairs[idx][j]) continue;
                    CompositeKernel k = responses.get(j).getKernel();
                    Kernel sched = k.getKernel(Role.SCHEDULE);
                    Kernel loc = k.getKernel(Role.LOCATION);
                    double sw = (sched != null) ? sched.compute(features) : 1.0;
                    double lw = (loc != null) ? loc.compute(features) : 1.0;
                    lastScheduleKernels[idx][j] = sw;
                    lastLocationKernel[idx][j] = lw;
                    personWeight += personResponseWeights[idx][j] * sw * lw;
                }
            }
            double newLW = (newRT == null) ? 0.0 : Math.log(Math.max(1e-8, personWeight));
            totalLW += newLW - perAgentLogWeight[idx];
            perAgentLogWeight[idx] = newLW;
        }

        @Override
        public void clear() {
            Arrays.fill(perAgentLogWeight, 0.0);
            totalLW = 0.0;
        }

        @Override
        public ExplainSummary clone() {
            return new ExplainSummary(this);
        }

        double getTotalLW() { return totalLW; }
    }

    @Override 
    public String toString() {
        String str = "ExplainRoundTripsByResponses: " + this.responses.size() + " responses, " + this.syntheticPopulation.size() + " persons";
        str += "\n Kernels: " + this.responses.getFirst().getKernel().toString();
        return str;
    }

    // ======== Getters ======== // 

    public List<SurveyResponse<ActivityNode>> getResponses() {
        return responses;
    }

    public double[][] getPersonResponseWeights() {
        return this.personResponseWeights;
    }

    public double[][] getLastScheduleKernels() {
        return this.lastScheduleKernels;
    }

    public double[][] getLastLocationKernels() {
        return this.lastLocationKernel;
    }    

}
