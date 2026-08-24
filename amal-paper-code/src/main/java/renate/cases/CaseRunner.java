package renate.cases;

/**
 * @Author: MichaelS
 */

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import renate.model.nodes.NodeWithActivity;
import renate.model.weights.ExplainRoundTripsByResponses;
import renate.model.weights.StrictlyEnforceCorrectHomeLocationWeight;
import renate.stateprocessors.ActivityDurationLogger;
import renate.stateprocessors.KernelMatrixLogger;
import renate.stateprocessors.VariablesLogger;
import se.vti.logger.ProgressLogger;
import se.vti.logger.XMLStateLogger;
import se.vti.roundtrips.common.Runner;
import se.vti.roundtrips.common.Scenario;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.multiple.MultiRoundTripJsonIO;
import se.vti.roundtrips.samplingweights.StrictlyPeriodicSchedule;
import se.vti.roundtrips.single.RoundTrip;
import se.vti.utils.misc.metropolishastings.MHStateProcessor;
import se.vti.utils.misc.metropolishastings.MHWeight;
import se.vti.utils.misc.metropolishastings.MHWeightContainer;
import se.vti.utils.misc.metropolishastings.MHWeightsToFileLogger;
import se.vti.utils.misc.metropolishastings.terminationcriteria.BlockAverageTerminationCriterion;
import se.vti.utils.misc.metropolishastings.terminationcriteria.CombinedAnyTerminationCriterion;
import se.vti.utils.misc.metropolishastings.terminationcriteria.FixedNumberOfIterationsTerminationCriterion;
import se.vti.utils.misc.metropolishastings.terminationcriteria.MinimumSampledCriterion;
import se.vti.utils.misc.metropolishastings.terminationcriteria.TerminationCriterion;


public class CaseRunner {
    private static final Logger logger = LogManager.getLogger(CaseRunner.class);

    private boolean verbose = false;

    private static final long MIN_SAMPLES = 10_000;
    private static final long CHECK_INTERVAL = 10_000;

    // Tolerance absorbs float rounding in re-simulated episode totals (a periodic schedule's
    // episodes tile exactly the period, so the sum can land at e.g. 24+4e-15, which the strict
    // > check in StrictlyPeriodicSchedule turns into -Infinity, freezing the chain).
    public static final double PERIOD_TOLERANCE_H = 1e-9;

    private final Runner<NodeWithActivity> runner;
    private final Scenario<NodeWithActivity> scenario;

    private String name;
    private List<NodeWithActivity> homeNodes;
    private Path outputDir;
    private long msgInterval;
    private boolean defaultPriorsFlag = false;
    private final List<TerminationCriterion<MultiRoundTrip<NodeWithActivity>>> terminationCriterions;

    // --- construction ---

    public CaseRunner(Scenario<NodeWithActivity> scenario, String name) {
        this(new Runner<>(scenario), scenario, name);
    }

    public CaseRunner(Runner<NodeWithActivity> runner, Scenario<NodeWithActivity> scenario, String name) {
        this.runner = runner;
        this.scenario = scenario;
        this.name = name;
        this.terminationCriterions = new ArrayList<>();
    }

    private void reconcileName(String name) {
        if (this.name == null) {
            this.name = name;
        } else if (!this.name.equals(name)) {
            throw new IllegalArgumentException(
                    "Name conflict: '" + this.name + "' vs '" + name + "'");
        }
    }

    public CaseRunner withDefaultLoggers(Path outputDir, long msgInterval, String name) {
        reconcileName(name);
        this.outputDir = outputDir;
        this.msgInterval = msgInterval;
        return this;
    }

    public CaseRunner withDefaultPriorWeights(List<NodeWithActivity> homeNodes, String name) {
        reconcileName(name);
        this.homeNodes = homeNodes;
        addDefaultPriorWeights();
        return this;
    }

    public CaseRunner withMaxIter(int nIter) {
        terminationCriterions.add(new FixedNumberOfIterationsTerminationCriterion<>(nIter));
        return this;
    }

    public CaseRunner withBlockConvergence() { return withBlockConvergence(null); }
    public CaseRunner withBlockConvergence(Double tolerance) { return withBlockConvergence(tolerance, 0); }
    public CaseRunner withBlockConvergence(Double tolerance, int minStationarySamples) {
        var crit = new BlockAverageTerminationCriterion<MultiRoundTrip<NodeWithActivity>>()
                // .setMinSamples((int) MIN_SAMPLES).setCheckInterval((int) CHECK_INTERVAL)
                .setConvergenceStatsFileName(outputDir.resolve("convergenceStats.tsv").toString());
        if (tolerance != null) crit.setRelativeVarianceTolerance(tolerance).setStandardizedMeanTolerance(tolerance);
        if (minStationarySamples > 0) {
            logger.info("Setting minimum number of stable samples to {}", minStationarySamples);
            // crit.setMinNumberOfStableSamples(minStationarySamples);
            crit.setMinSamples(minStationarySamples);
        } 
        terminationCriterions.add(crit);
        return this;
    }

    public CaseRunner verbose() {
        this.verbose = true;
        return this;
    }

    private TerminationCriterion<MultiRoundTrip<NodeWithActivity>> constructTerminationCriterion() {
        return switch (terminationCriterions.size()) {
            case 0 -> null;
            case 1 -> terminationCriterions.get(0);
            default -> {
                var tc = new CombinedAnyTerminationCriterion<>(terminationCriterions.get(0));
                terminationCriterions.stream().skip(1).forEach(tc::add);
                yield tc;                
            }
        };
    }

    public CaseRunner setUniformPrior() { 
        runner.setPopulationUniformPrior(); 
        logger.info("\tUniform prior added");
        return this; 
    }
    
    public CaseRunner setPopulationBinomialPrior(double meanRoundTripSize) { 
        logger.info("Using binomial prior");
        runner.setPopulationBinomialPrior(meanRoundTripSize);  
        logger.info("\tPopulation Binomial prior added");        
        return this;        
    }
    public CaseRunner setInitialState(MultiRoundTrip<NodeWithActivity> s) { runner.setInitialState(s); return this; }
    public CaseRunner setNumberOfIterations(long n) { runner.setNumberOfIterations(n); return this; }
    public CaseRunner setMessageInterval(long n) { runner.setMessageInterval(n); return this; }
    public CaseRunner setTerminationCriterion(TerminationCriterion<MultiRoundTrip<NodeWithActivity>> c) { runner.setTerminationCriterion(c); return this; }
    public CaseRunner addIndividualWeight(MHWeight<RoundTrip<NodeWithActivity>> w) { 
        logger.info("Adding individual weight {}", w.name());
        runner.addIndividualWeight(w); return this; 
    }
    public CaseRunner addPopulationWeight(MHWeight<MultiRoundTrip<NodeWithActivity>> w, double f) { runner.addPopulationWeight(w, f); return this; }
    public CaseRunner addStateProcessor(MHStateProcessor<MultiRoundTrip<NodeWithActivity>> p) { runner.addStateProcessor(p); return this; }
    public CaseRunner addSampleExtractor(String name, Function<MultiRoundTrip<NodeWithActivity>, Double> f) { runner.addSampleExtractor(name, f); return this; }



    // --- default priors ---
    public void addDefaultPriorWeights() {
        if (defaultPriorsFlag) {
            throw new IllegalStateException("Default priors already set!");
        }
        logger.info("Partition {}: Adding weights:", name);
        runner.setPopulationUniformPrior();
        logger.info("\tUniform prior added");
        runner.addIndividualWeight(new StrictlyPeriodicSchedule<>(scenario.getPeriodLength_h() + PERIOD_TOLERANCE_H));
        logger.info("\tadded StrictlyPeriodSchedule({}) as individualWeight", scenario.getPeriodLength_h());
        runner.addIndividualWeight(new StrictlyEnforceCorrectHomeLocationWeight(homeNodes));
        logger.info("\tAdded StrictlyEnforeCorrectHomeLocationWeight as individualWeight");
        defaultPriorsFlag = true;
    }

    // --- loggers ---
    public void addDefaultLoggers() {
        // ExplainRoundTripsByResponses kernelWeights = findKernelWeights();
        // if (kernelWeights != null) {
        //     runner.addStateProcessor(new KernelMatrixLogger<>(msgInterval, outputDir.resolve("KernelMatrices"), kernelWeights));
        //     }
        runner.addStateProcessor(new VariablesLogger<>(
                msgInterval, outputDir.resolve("stateVariables.tsv").toString()));
        runner.addStateProcessor(new MHWeightsToFileLogger<>(
                msgInterval, getWeightsContainer(), outputDir.resolve("weights.tsv").toString()));
        runner.addStateProcessor(new XMLStateLogger<>(msgInterval, outputDir));
        runner.addStateProcessor(new ActivityDurationLogger<>(
                msgInterval, outputDir.resolve("activityDurations.tsv").toString()));
        runner.addStateProcessor(new ProgressLogger<>(msgInterval, name));
    }
    private ExplainRoundTripsByResponses findKernelWeights() {
        for (MHWeight<?> w : getWeightsContainer().getComponentsView()) {
            if (w instanceof ExplainRoundTripsByResponses erbr) {
                return erbr;
            }
        }
        return null;
    }

    // --- execution ---
    public MultiRoundTrip<NodeWithActivity> run() {
        if (outputDir != null) {
            addDefaultLoggers();
        }
        logger.info("Running with weights: ");
        for (var w : getSamplingWeightsView()) {
            logger.info("\t {}", w.getClass());
        }

        logger.info("Partition {}: Starting MCMC", name);
        var tc = constructTerminationCriterion();
        if (tc != null) runner.setTerminationCriterion(tc);

        if (!verbose) runner.setMessageInterval(0);
        runner.run();

        MultiRoundTrip<NodeWithActivity> finalState = runner.getFinalState();
        if (outputDir != null) {
            writeFinalState(finalState);
        }
        return finalState;
    }

    private void writeFinalState(MultiRoundTrip<NodeWithActivity> finalState) {
        Path target = outputDir.resolve("finalState.json");
        try {
            MultiRoundTripJsonIO.singleton().writeToFile(finalState, target.toString());
            logger.info("Partition {}: Wrote final state to file {}", name, target);
        } catch (IOException e) {
            logger.error("Partition {}: Failed to write final state to {}", name, target, e);
        }
    }

    // --- accessors ---

    public MultiRoundTrip<NodeWithActivity> getFinalState() {
        return runner.getFinalState();
    }

    public List<MHWeight<MultiRoundTrip<NodeWithActivity>>> getSamplingWeightsView() {
        return runner.getSamplingWeightsView();
    }

    public MHWeightContainer<MultiRoundTrip<NodeWithActivity>> getWeights() {
        return getWeightsContainer();
    }

    public MHWeightContainer<MultiRoundTrip<NodeWithActivity>> getWeightsContainer() {
        MHWeightContainer<MultiRoundTrip<NodeWithActivity>> weights = new MHWeightContainer<>();
        for (MHWeight<MultiRoundTrip<NodeWithActivity>> w : runner.getSamplingWeightsView()) {
            weights.add(w);
        }
        return weights;
    }
}