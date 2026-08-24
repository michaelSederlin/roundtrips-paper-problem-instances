package disaggregate.model.weights;

import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import disaggregate.kernels.CompositeKernel;
import disaggregate.kernels.Kernel;
import disaggregate.kernels.KernelSummary;
import disaggregate.kernels.Role;
import disaggregate.kernels.TravelerKernel;
import disaggregate.model.Person;
import disaggregate.model.SurveyResponse;
import disaggregate.model.ActivityNode;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.multiple.MultiRoundTripSummary;
import se.vti.roundtrips.single.RoundTrip;

public class ExplainRoundTripsByResponses implements ResponseWeight<ActivityNode>{
    private static final Logger logger = LogManager.getLogger(ExplainRoundTripsByResponses.class);
    private final List<SurveyResponse<ActivityNode>> responses;
    private final List<Person> syntheticPopulation;
    private final KernelSummary kernelSummary;
    private final ExplainSummary explainSummary;

    private final double[][] personResponseWeights;
    private double[][] lastScheduleKernels;
    private double[][] lastLocationKernel;
    private boolean normalize = false;

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
                
                Kernel<?> tkRaw = k.getKernel(Role.TRAVELER);
                if (tkRaw != null && !(tkRaw instanceof TravelerKernel)) {
                    throw new IllegalStateException("TRAVELER Role must be a TravelerKernel, got " + tkRaw.getClass());
                }
                TravelerKernel tk = (TravelerKernel) tkRaw;
                personWeights[jj] = (tk != null)
                ? tk.compute(TravelerKernel.Features.from(syntheticPopulation.get(ii)))
                : 1.0;
                
                if (personWeights[jj] < threshold) {
                    this.excludedPairs[ii][jj] = true;
                } 
            }
            
            boolean normalization = false;
            if (normalization) {
                double sum = 0;
                for (double w: personWeights) sum += w;
                if (sum < 1e-8) {
                    logger.warn("""
                        Person {} has very low total kernel weight across all responses: {}. 
                        This person will have near-zero weight in the MH sampling. 
                        Consider lowering the threshold (currently {}) or checking if the kernels are correctly specified.""",
                        ii, sum, threshold);
                }
                    // throw new RuntimeException("Synthetic person does not match any response: " + syntheticPopulation.get(ii));
                for (int responseIdx = 0; responseIdx < responses.size(); responseIdx++) { personWeights[responseIdx] /= sum; }
            }
        }
        
        this.kernelSummary = new KernelSummary(syntheticPopulation.size(), this.excludedPairs);

        for (SurveyResponse<ActivityNode> response : responses) {
            response.getKernel().configureRequiredSummary(this.kernelSummary);
        }

        this.explainSummary = new ExplainSummary(syntheticPopulation.size(), this.kernelSummary);
    }

    @Override
    public double logWeight(MultiRoundTrip<ActivityNode> multiRoundTrip) {
        return multiRoundTrip.getSummary(ExplainSummary.class).getTotalLW();
    }

    public void configureRequiredSummary(MultiRoundTrip<ActivityNode> mrt) {
        mrt.addSummary(kernelSummary);   // KernelSummary first — ExplainSummary reads it
        mrt.addSummary(explainSummary);  // ExplainSummary second
        mrt.recomputeSummaries();
    }

    class ExplainSummary implements MultiRoundTripSummary<ActivityNode> {
        private final double[] perAgentLogWeight;
        private double totalLW = 0.0;
        private final KernelSummary ksumRef;

        ExplainSummary(int nAgents, KernelSummary ksumRef) {
            this.perAgentLogWeight = new double[nAgents];
            this.ksumRef = ksumRef;
        }

        private ExplainSummary(ExplainSummary parent) {
            this.perAgentLogWeight = parent.perAgentLogWeight.clone();
            this.totalLW = parent.totalLW;
            this.ksumRef = parent.ksumRef;
        }

        // Hot path: called by setRoundTripAndUpdateSummaries. Gets KernelSummary from
        // the container — always correct, even on cloned proposal states.
        @Override
        public void update(RoundTrip<ActivityNode> newRT, MultiRoundTrip<ActivityNode> state) {
            recompute(newRT.getIndex(), newRT, state.getSummary(KernelSummary.class));
        }

        // Fallback for recomputeSummaries(). Uses stored ksumRef which is correct at
        // startup (the only time recomputeSummaries is called). Never reached on clones.
        @Override
        public void update(int idx, RoundTrip<ActivityNode> oldRT, RoundTrip<ActivityNode> newRT) {
            recompute(idx, newRT, ksumRef);
        }

        private void recompute(int idx, RoundTrip<ActivityNode> newRT, KernelSummary ksum) {
            double personWeight = 0.0;
            if (newRT != null) {
                for (int j = 0; j < responses.size(); j++) {
                    if (excludedPairs[idx][j]) continue;
                    CompositeKernel k = responses.get(j).getKernel();
                    Kernel<?> sched = k.getKernel(Role.SCHEDULE);
                    Kernel<?> loc = k.getKernel(Role.LOCATION);
                    double sw = (sched != null) ? sched.compute(newRT, ksum) : 1.0;
                    double lw = (loc != null) ? loc.compute(newRT, ksum) : 1.0;
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

    double normalize(double[] weights, double sum) {
        for (int i = 0; i < weights.length; i++) {
            weights[i] /= sum;
        }
        return sum;
    }

    // ======== Getters ======== // 

    public List<SurveyResponse<ActivityNode>> getResponses() {
        return responses;
    }

    public KernelSummary getKernelSummary() {
        return kernelSummary;
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