package disaggregate;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

import renate.cases.CaseRunner;
import renate.kernels.commons.CompositeKernel;
import renate.kernels.commons.Role;
import renate.kernels.schedulekernels.GaussianScheduleOverlapKernel;
import renate.kernels.schedulekernels.NumberOfStopsKernel;
import renate.kernels.schedulekernels.TotalTravelTimeKernel;
import renate.kernels.travelerkernels.TravelerKernel;
import renate.model.Person;
import renate.model.SurveyResponse;
import renate.model.nodes.NodeWithActivity;
import renate.model.weights.ExplainRoundTripsByResponses;
import renate.model.weights.GeometricMeanStayingWeight;
import renate.model.weights.MovementWeight;
import renate.model.weights.StrictlyEnforceCorrectHomeLocationWeight;
import renate.rvu.MDDR;
import renate.rvu.MDDRRepository;
import renate.rvu.UPBD;
import renate.rvu.UPBDRepository;
import renate.stateprocessors.PopulationWriter;
import renate.utils.LoadOpportunityWeights;
import renate.utils.MatrixDataReader;
import renate.utils.ScenarioUtils;
import se.vti.roundtrips.common.Scenario;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.multiple.MultiRoundTripJsonIO;
import se.vti.roundtrips.samplingweights.StrictlyPeriodicSchedule;
import se.vti.roundtrips.samplingweights.StrictlyRequireMinNumberOfStops;
import se.vti.roundtrips.single.RoundTrip;
import se.vti.utils.misc.Tuple;

/**
 * Minimal AMAL runner -- the version accompanying the paper.
 *
 * Samples a synthetic population of round trips with Metropolis-Hastings, under
 * a prior plus up to three weights:
 *
 * <ul>
 *   <li><b>kernel weight</b> -- explains the sampled round trips by the survey
 *       responses, through a traveler kernel (socio-demographics) times a
 *       schedule kernel (number of stops, total travel time, schedule overlap);</li>
 *   <li><b>staying weight</b> -- rewards time spent where the activity's
 *       opportunity weights are high;</li>
 *   <li><b>movement weight</b> -- pulls the sampled origin-destination flows
 *       towards an observed OD matrix.</li>
 * </ul>
 *
 * The population is split into partitions that are sampled in parallel, each
 * with its own chain and its own output directory. Population weights are
 * evaluated within a partition, so the number of partitions is part of the model
 * specification and not just a speed setting.
 *
 * This is a deliberately reduced version of {@code renate.cases.amal.AmalRunner}:
 * the model setup is the same, but the plotting, post-processing and partition
 * merging (all of which shell out to Python) are left out, as are the option
 * groups the paper does not vary. Each partition writes {@code stateVariables.tsv},
 * {@code weights.tsv}, {@code convergenceStats.tsv}, {@code activityDurations.tsv},
 * {@code states.xml}, {@code finalState.json}, {@code population.tsv} and
 * {@code respondents.tsv}.
 */
@Command(name = "amal", mixinStandardHelpOptions = true,
        description = "Minimal AMAL runner: MCMC sampling of a synthetic round trip population.")
    public class Runner implements Callable<Integer> {

    private static final Logger logger = LogManager.getLogger(Runner.class);

    /** Floor on the staying / movement weight contributions, keeping log weights finite. */
    private static final double WEIGHT_EPSILON = 1e-5;

    /** Mean round trip size of the binomial prior. */
    private static final double BINOMIAL_PRIOR_MEAN_ROUND_TRIP_SIZE = 3.125;

    @Mixin Options opts;

    public static void main(String[] args) {
        System.exit(new CommandLine(new Runner()).execute(args));
    }

    @Override
    public Integer call() throws Exception {
        Path scenarioDir = Path.of(opts.scenarioDir);
        Path outputDir = Path.of(opts.outputDir).resolve(opts.name);
        Files.createDirectories(outputDir);

        ThreadContext.put("runId", "amal");
        ThreadContext.put("logDir", outputDir.toString());
        logConfiguration(scenarioDir, outputDir);

        UPBD upbd = new UPBDRepository().fromCSV(scenarioDir.resolve("upbd.csv")).load();
        MDDR mddr = new MDDRRepository().fromCSV(scenarioDir.resolve("mddr.csv")).load();
        List<Person> agents = ScenarioUtils.loadRespondents(upbd, mddr, upbd.size());
        List<Person> respondents = selectRespondents(agents);
        logger.info("Loaded {} agents, of which {} act as survey respondents", agents.size(), respondents.size());

        Map<Tuple<String, String>, Double> odMatrix = opts.withMovementWeight
                ? MatrixDataReader.readWithSemicolonSeparator(scenarioDir.resolve("od_matrix.tsv"))
                : null;

        int nPartitions = (opts.maxProcesses > 0) ? opts.maxProcesses : Runtime.getRuntime().availableProcessors();
        List<List<Person>> partitions = splitEvenly(agents, nPartitions);
        logger.info("Split {} agents into {} partitions", agents.size(), partitions.size());

        ExecutorService pool = Executors.newFixedThreadPool(partitions.size());
        try {
            List<Future<?>> futures = new ArrayList<>();
            int partitionOffset = 0;
            for (int i = 0; i < partitions.size(); i++) {
                String partition = String.valueOf(i);
                List<Person> partitionAgents = partitions.get(i);
                int offset = partitionOffset;
                futures.add(pool.submit(() -> {
                    runPartition(partition, partitionAgents, respondents, odMatrix,
                            outputDir.resolve(partition), scenarioDir, offset);
                    return null;
                }));
                partitionOffset += partitionAgents.size();
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdown();
        }

        logger.info("All partitions finished. Results are under {}", outputDir.toAbsolutePath());
        return 0;
    }

    /** Samples one partition's chain. Runs on its own thread, with its own log file. */
    void runPartition(
            String partition,
            List<Person> agents,
            List<Person> respondents,
            Map<Tuple<String, String>, Double> odMatrix,
            Path partitionDir,
            Path scenarioDir,
            int partitionOffset) throws Exception {

        Files.createDirectories(partitionDir);
        ThreadContext.put("runId", partition);
        ThreadContext.put("logDir", partitionDir.toString());
        try {
            logger.info("Partition {}: {} agents on thread {}",
                    partition, agents.size(), Thread.currentThread().getName());

            Scenario<NodeWithActivity> scenario = new Scenario<>(new Random(opts.seed));
            scenario.setNumberOfTimeBins((int) (opts.horizon / opts.timeBinSize));
            scenario.setTimeBinSize_h(opts.timeBinSize);
            ScenarioUtils.loadNetworkFromTSV(scenario, scenarioDir);

            Map<NodeWithActivity, Double> opportunityWeights = opts.withStayingWeight
                    ? LoadOpportunityWeights.load(scenarioDir.resolve("opportunity_weights.csv"), scenario)
                    : null;

            new PopulationWriter(agents, partitionDir.resolve("population.tsv").toString()).write();
            new PopulationWriter(respondents, partitionDir.resolve("respondents.tsv").toString()).write();

            List<NodeWithActivity> homeNodes = ScenarioUtils.findHomes(scenario, agents);
            MultiRoundTrip<NodeWithActivity> initialState =
                    createInitialState(scenario, agents, homeNodes, partition, partitionOffset);

            CaseRunner runner = new CaseRunner(scenario, partition)
                    .setInitialState(initialState)
                    .withDefaultLoggers(partitionDir, opts.effectiveMsgInterval(), partition)
                    .withBlockConvergence(opts.stationarityTolerance);

            logger.info("Partition {}: adding weights", partition);

            runner.setPopulationBinomialPrior(BINOMIAL_PRIOR_MEAN_ROUND_TRIP_SIZE);
            runner.addIndividualWeight(new StrictlyRequireMinNumberOfStops<>(1));
            runner.addIndividualWeight(new StrictlyEnforceCorrectHomeLocationWeight(homeNodes));

            GeometricMeanStayingWeight stayingWeight =
                    new GeometricMeanStayingWeight(opportunityWeights, WEIGHT_EPSILON)
                            .withExponent(opts.stayingWeightExponent);
            stayingWeight.configureRequiredSummary(initialState);
            runner.addPopulationWeight(stayingWeight, opts.stayMixingWeight);
            logger.info("\tstaying weight (geometric mean), mixing weight {}", opts.stayMixingWeight);


            MovementWeight movementWeight = new MovementWeight(odMatrix, WEIGHT_EPSILON)
                    .withExponent(opts.movementWeightExponent)
                    .withGeometricMean();
            movementWeight.configureRequiredSummary(initialState);
            runner.addPopulationWeight(movementWeight, opts.moveMixingWeight);
            logger.info("\tmovement weight (geometric mean), mixing weight {}", opts.moveMixingWeight);


            if (opts.withKernelWeights) {
                ExplainRoundTripsByResponses kernelWeights = new ExplainRoundTripsByResponses(
                        buildKernelResponses(respondents), agents, opts.kernelThreshold);
                kernelWeights.configureRequiredSummary(initialState);
                runner.addPopulationWeight(kernelWeights, opts.kernelFactor);
                logger.info("\tkernel weight over {} responses, factor {}", respondents.size(), opts.kernelFactor);
            }

            if (opts.maxIter != null) {
                runner.withMaxIter(opts.maxIter);
            }

            runner.setMessageInterval(0);

            // CaseRunner.run() writes finalState.json alongside the sample loggers.
            runner.run();
            logger.info("Partition {}: done, results in {}", partition, partitionDir.toAbsolutePath());
        } finally {
            ThreadContext.clearAll();
        }
    }

    /**
     * Starting point of the chain: either the matching slice of an earlier run's final state,
     * or a fresh state in which everybody stays home.
     */
    private MultiRoundTrip<NodeWithActivity> createInitialState(
            Scenario<NodeWithActivity> scenario,
            List<Person> agents,
            List<NodeWithActivity> homeNodes,
            String partition,
            int partitionOffset) throws Exception {

        if (opts.warmStartFile == null) {
            return ScenarioUtils.createInitialMultiRoundTrip(scenario, homeNodes, agents);
        }
        if (!Files.exists(Path.of(opts.warmStartFile))) {
            throw new FileNotFoundException("Missing warm start file " + opts.warmStartFile);
        }

        MultiRoundTrip<NodeWithActivity> globalState =
                MultiRoundTripJsonIO.singleton().readFromFile(scenario, opts.warmStartFile);
        MultiRoundTrip<NodeWithActivity> initialState = new MultiRoundTrip<>(agents.size());
        for (int i = 0; i < agents.size(); i++) {
            RoundTrip<NodeWithActivity> globalRoundTrip = globalState.getRoundTrip(partitionOffset + i);
            RoundTrip<NodeWithActivity> localRoundTrip =
                    new RoundTrip<>(i, globalRoundTrip.cloneNodes(), globalRoundTrip.cloneDepartures());
            localRoundTrip.setEpisodes(scenario.getOrCreateSimulator().simulate(localRoundTrip));
            initialState.setRoundTripAndUpdateSummaries(i, localRoundTrip);
        }
        logger.info("Partition {}: warm-started from {} at global offset {}",
                partition, opts.warmStartFile, partitionOffset);
        return initialState;
    }

    /**
     * One kernel per respondent: a traveler kernel over socio-demographics, mixed with a
     * schedule kernel built from whichever of the three schedule components carry a
     * non-zero weight.
     */
    List<SurveyResponse<NodeWithActivity>> buildKernelResponses(List<Person> respondents) {
        double[] travelerParams = { opts.lAge, opts.lInc, opts.eta };
        List<SurveyResponse<NodeWithActivity>> responses = new ArrayList<>();

        for (Person respondent : respondents) {
            CompositeKernel schedule = new CompositeKernel();
            if (opts.nStopsWeight > 0) {
                schedule.add(new NumberOfStopsKernel(
                        respondent.getTrip().getStopCount(), opts.nStopsBandwidth), opts.nStopsWeight);
            }
            if (opts.tttWeight > 0) {
                schedule.add(new TotalTravelTimeKernel(
                        respondent.getTrip().getTotalTravelTime_h(), opts.tttBandwidth), opts.tttWeight);
            }
            if (opts.scheduleOverlapWeight > 0) {
                schedule.add(new GaussianScheduleOverlapKernel(
                        respondent, opts.scheduleOverlapBandwidth), opts.scheduleOverlapWeight);
            }

            CompositeKernel kernel = new CompositeKernel()
                    .add(new TravelerKernel(respondent, travelerParams), Role.TRAVELER)
                    .add(schedule, Role.SCHEDULE);
            kernel.setMixtureWeights(new double[] { opts.travelerMixWeight, opts.scheduleMixWeight });
            responses.add(new SurveyResponse<>(respondent, kernel));
        }
        return responses;
    }

    /** Every person responds, unless {@code --nRespondents} asks for a seeded random subset. */
    private List<Person> selectRespondents(List<Person> population) {
        if (opts.nRespondents == null) {
            return population;
        }
        List<Person> shuffled = new ArrayList<>(population);
        Collections.shuffle(shuffled, new Random(opts.seed));
        return List.copyOf(shuffled.subList(0, Math.min(opts.nRespondents, shuffled.size())));
    }

    /** Splits into n contiguous parts whose sizes differ by at most one. */
    static <T> List<List<T>> splitEvenly(List<T> list, int n) {
        int base = list.size() / n;
        int remainder = list.size() % n;
        List<List<T>> parts = new ArrayList<>(n);
        int from = 0;
        for (int i = 0; i < n; i++) {
            int to = from + base + (i < remainder ? 1 : 0);
            parts.add(list.subList(from, to));
            from = to;
        }
        return parts;
    }

    private void logConfiguration(Path scenarioDir, Path outputDir) {
        logger.info("===== AMAL run setup =====");
        logger.info("  name={}  |  seed={}  |  maxIter={}  |  msgInterval={}",
                opts.name, opts.seed, opts.maxIter != null ? opts.maxIter : "until convergence",
                opts.effectiveMsgInterval());
        logger.info("  maxProcesses={}  |  stationarityTolerance={}",
                opts.maxProcesses > 0 ? opts.maxProcesses : "auto", opts.stationarityTolerance);
        logger.info("  timeBinSize={}h  |  horizon={}h", opts.timeBinSize, opts.horizon);
        logger.info("  scenarioDir={}", scenarioDir.toAbsolutePath());
        logger.info("  outputDir={}", outputDir.toAbsolutePath());
        logger.info("  warmStartFile={}", opts.warmStartFile != null ? opts.warmStartFile : "none");
        logger.info("  prior={}  |  enforceCorrectHomeLocation={}",
                opts.binomialPrior ? "binomial" : "uniform", opts.enforceCorrectHomeLocation);
        logger.info("  kernelWeights={}", opts.withKernelWeights
                ? String.format("on (factor=%s, traveler[lAge=%s, lInc=%s, eta=%s, threshold=%s, mix=%s],"
                        + " schedule[nStops=%s/bw=%s, ttt=%s/bw=%s, overlap=%s/bw=%s, mix=%s])",
                        opts.kernelFactor, opts.lAge, opts.lInc, opts.eta, opts.kernelThreshold,
                        opts.travelerMixWeight, opts.nStopsWeight, opts.nStopsBandwidth,
                        opts.tttWeight, opts.tttBandwidth, opts.scheduleOverlapWeight,
                        opts.scheduleOverlapBandwidth, opts.scheduleMixWeight)
                : "off");
        logger.info("  stayingWeight={}", opts.withStayingWeight
                ? String.format("on (mix=%s, exponent=%s)", opts.stayMixingWeight, opts.stayingWeightExponent)
                : "off");
        logger.info("  movementWeight={}", opts.withMovementWeight
                ? String.format("on (mix=%s, exponent=%s)", opts.moveMixingWeight, opts.movementWeightExponent)
                : "off");
        logger.info("==========================");
    }
}
