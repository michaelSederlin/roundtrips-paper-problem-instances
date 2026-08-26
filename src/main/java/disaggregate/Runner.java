package disaggregate;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import common.*;
import common.SplitPopulationJoiner;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import se.vti.utils.misc.metropolishastings.MHWeight;
import se.vti.utils.misc.metropolishastings.MHWeightContainer;
import se.vti.utils.misc.metropolishastings.MHWeightsToFileLogger;
import se.vti.utils.misc.metropolishastings.terminationcriteria.BlockAverageTerminationCriterion;
import se.vti.utils.misc.metropolishastings.terminationcriteria.CombinedAnyTerminationCriterion;
import se.vti.utils.misc.metropolishastings.terminationcriteria.FixedNumberOfIterationsTerminationCriterion;
import disaggregate.model.*;
import disaggregate.weights.*;
import disaggregate.rvu.SurveyReader;
import disaggregate.utils.LoadOpportunityWeights;
import disaggregate.utils.MatrixDataReader;
import disaggregate.utils.ScenarioUtils;
import disaggregate.utils.UniqueActivityNodeSummary;
import se.vti.roundtrips.common.Scenario;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.multiple.MultiRoundTripJsonIO;
import se.vti.roundtrips.samplingweights.StrictlyRequireMinNumberOfStops;
import se.vti.roundtrips.single.RoundTrip;
import se.vti.utils.misc.Tuple;

@Command(name = "Amal", mixinStandardHelpOptions = true)
public class Runner implements Callable<Integer> {
    static final Logger logger = LogManager.getLogger(Runner.class);
    static final DateTimeFormatter RUN_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    static final ZoneId RUN_TIMESTAMP_ZONE = ZoneId.of("Europe/Stockholm");
    static final int MINIMUM_WINDOW = 10;

    @CommandLine.Spec CommandSpec spec; 
    @Mixin Options opts;
    public static void main(String[] args) throws Exception {
        System.exit(new CommandLine(new Runner()).execute(args));
    }

    static Path resolveRunOutputPath(Path outputPath) {
        String runName = System.getenv("RUN_NAME");
        String timestamp = LocalDateTime.now(RUN_TIMESTAMP_ZONE).format(RUN_TIMESTAMP_FORMAT);
        String folderName = (runName == null || runName.isBlank()) ? timestamp : runName + "_" + timestamp;
        return outputPath.resolve(folderName);
    }

    static void copyArgumentsFileIfPresent(Path runOutputPath) throws IOException {
        Path argsFile = Path.of("amal-arguments.txt");
        if (Files.exists(argsFile)) {
            Files.copy(argsFile, runOutputPath.resolve("arguments.txt"), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public Integer call() throws Exception {
        Path scenarioPath = Path.of(opts.scenario_dir);
        Path outputPath = Path.of(opts.output_dir).resolve(opts.name);
        Path runOutputPath = resolveRunOutputPath(outputPath);
        int effectiveMsgInterval = (opts.msg_interval < 0) ? (opts.max_number_of_iterations != null ? opts.max_number_of_iterations / 100 : 1000) : opts.msg_interval;

        Files.createDirectories(runOutputPath);
        ThreadContext.put("runId", "amal");
        ThreadContext.put("logDir", runOutputPath.toString());
        copyArgumentsFileIfPresent(runOutputPath);

        opts.saveArgs(runOutputPath.resolve("resolved-arguments.txt").toString());

        logger.info("===== AMAL Parallel Simulation setup =====");
        logger.info("--- Run ---");
        logger.info("  name={}  |  maxProcesses={}", opts.name, opts.max_processes > 0 ? opts.max_processes : "auto");
        logger.info("  max_number_of_iterations={}  |  seed={}  |  msgInterval={}", opts.max_number_of_iterations, opts.seed, effectiveMsgInterval);
        logger.info("  timeBinSize={}h  |  horizon={}h  |  stationarityTolerance={}", opts.time_bin_size, opts.horizon, opts.stationarity_tolerance);
        logger.info("--- Paths ---");
        logger.info("  scenarioDir:   {}", scenarioPath.toAbsolutePath());
        logger.info("  outputDir:     {}", outputPath.toAbsolutePath());
        logger.info("  runOutputDir:  {}", runOutputPath.toAbsolutePath());
        logger.info("  warmStartFile: {}", opts.warm_start_file != null ? opts.warm_start_file : "none");
        logger.info("--- Traveler kernel ---");
        logger.info("  lAge={}  |  lInc={}  |  eta={}  |  threshold={}  |  travelerMixWeight={}",
                opts.lambda_age, opts.lambda_income, opts.eta, opts.traveler_kernel_threshold, opts.traveler_kernel_mixing_weight);
        logger.info("--- Schedule kernel (withKernelWeights={}, factor={}) ---", opts.with_kernel_weights, opts.kernel_mixing_weight);
        logger.info("  nStops:  bandwidth={}  |  weight={}", opts.n_stops_bandwidth, opts.n_stops_mixing_weight);
        logger.info("  overlap:  weight={}", opts.schedule_overlap_mixing_weight);
        logger.info("  scheduleMixWeight={}", opts.schedule_kernel_mixing_weight);
        logger.info("--- Staying weight (withStayingWeight={}) ---",
                opts.with_staying_weight);
        logger.info("  stayMixingWeight={}  |  normalizeWeightsAcrossNodes={} ",
                opts.stay_mixing_weight, opts.normalize_weights_across_nodes);
        logger.info("--- Movement weight (withMovementWeight={}) ---", opts.with_movement_weight);
        logger.info("  moveMixingWeight={}",
                opts.move_mixing_weight);
        logger.info("===========================================");

        // LOAD FULL POPULATION
        List<Person> population = SurveyReader.readPopulation(
                scenarioPath.resolve("upbd.csv"),
                scenarioPath.resolve("mddr.csv"));
        List<Person> completeAgents = new ArrayList<>();
        List<Person> completeRespondents = new ArrayList<>();

        for (Person p : population) completeRespondents.add(p);

        for (Person p : population) completeAgents.add(p);

        logger.info("Loaded {} respondents and {} agents from {} persons", completeRespondents.size(), completeAgents.size(), population.size());

        Map<Tuple<String, String>, Double> odMatrix = null;
        if (opts.with_movement_weight) {
            odMatrix = MatrixDataReader.readWithSemicolonSeparator(scenarioPath.resolve("od_matrix.tsv"));
        }

        final Map<Tuple<String, String>, Double> finalOdMatrix = odMatrix;

        final List<Person> finalCompleteRespondents;
        if (opts.n_respondents != null) {
            List<Person> shuffled = new ArrayList<>(completeRespondents);
            Collections.shuffle(shuffled, new Random(opts.seed));
            finalCompleteRespondents = shuffled.subList(0, Math.min(opts.n_respondents, shuffled.size()));
        } else {
            finalCompleteRespondents = completeRespondents;
        }
        logger.info("Kernel respondents: {} of {}", finalCompleteRespondents.size(), population.size());

        int nParts = (opts.max_processes > 0) ? opts.max_processes : Runtime.getRuntime().availableProcessors();
        List<List<Person>> partitions = splitEvenly(completeAgents, nParts);
        logger.info("Split {} persons into {} partitions", completeAgents.size(), partitions.size());

        ExecutorService pool = Executors.newFixedThreadPool(nParts);
        PerThreadConsole.install();
        
        List<Future<?>> futures = new ArrayList<>();
        int from = 0;
        for (int i = 0; i < partitions.size(); i++) {
            final String partName = String.valueOf(i);
            final List<Person> agents = partitions.get(i);
            final Path partOutputDir = runOutputPath.resolve(partName);
            final int partFrom = from;
            futures.add(pool.submit(() -> {
                try {
                    runPartition(partName, agents, finalCompleteRespondents,
                            finalOdMatrix,
                            effectiveMsgInterval, partOutputDir, scenarioPath, opts.warm_start_file, partFrom);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return null;
            }));
            from += agents.size();
        }
        pool.shutdown();
        for (var f : futures) f.get();

        mergePartitions(runOutputPath);

        return 0;
    }

    void mergePartitions(Path runOutputPath) throws IOException, InterruptedException {
        logger.info("Merging partitions into {}", runOutputPath.resolve("partitions_merged"));
        Files.createDirectories(runOutputPath.resolve("partitions_merged"));


        new SplitPopulationJoiner(runOutputPath, "population.tsv", "population_merged.tsv").join();
        new SplitStatesJoiner(runOutputPath, "population.tsv", "convergenceStats.tsv", MINIMUM_WINDOW).join();

        Path firstPartitionRespondents;
        try (var partitionDirs = Files.list(runOutputPath)) {
            firstPartitionRespondents = partitionDirs
                    .filter(p -> Files.isDirectory(p) && p.getFileName().toString().matches("\\d+"))
                    .sorted()
                    .map(p -> p.resolve("respondents.tsv"))
                    .filter(Files::exists)
                    .findFirst()
                    .orElse(null);
        }
        if (firstPartitionRespondents != null) {
            Files.copy(firstPartitionRespondents,
                    runOutputPath.resolve("partitions_merged").resolve("respondents.tsv"),
                    StandardCopyOption.REPLACE_EXISTING);
        } else {
            logger.warn("No partition respondents.tsv found under {}; skipping respondents copy", runOutputPath);
        }
    }

    void runPartition(
        String partName,
        List<Person> agents,
        List<Person> respondents,
        Map<Tuple<String, String>, Double> odMatrix,
        int effectiveMsgInterval,
        Path partOutputDir,
        Path scenarioPath,
        String warmStartFile,
        int partFrom
        ) throws Exception {
        Files.createDirectories(partOutputDir);
        ThreadContext.put("runId", partName);
        ThreadContext.put("logDir", partOutputDir.toString());

        try {
            logger.info("Starting partition {} ({} agents) on thread {}", partName, agents.size(), Thread.currentThread().getName());

            Scenario<ActivityNode> scenario = new Scenario<>(new Random(opts.seed));
            scenario.setNumberOfTimeBins((int) (opts.horizon / opts.time_bin_size));
            scenario.setTimeBinSize_h(opts.time_bin_size);
            ScenarioUtils.loadNetworkFromTSV(scenario, scenarioPath);

            // Activities restricted to a single (highest-opportunity) node per round trip.
            Set<Activity> uniqueActivities = EnumSet.noneOf(Activity.class);
            for (Map.Entry<Activity, Boolean> entry : opts.unique_activity_nodes.entrySet()) {
                if (Boolean.TRUE.equals(entry.getValue())) uniqueActivities.add(entry.getKey());
            }
            if (!uniqueActivities.isEmpty()) {
                // lambda2PerActivity.keySet() is the schedule overlap kernel's scored activity
                // set, so a restricted activity without a median duration would be collapsed
                // here but never scored there.
                for (Activity act : uniqueActivities) {
                    if (!opts.median_durations.containsKey(act)) {
                        throw new IllegalArgumentException("--unique-activity-node " + act
                                + " has no --median-duration entry, so it would never be scored"
                                + " by the schedule overlap kernel");
                    }
                }
            }

            Map<ActivityNode, Double> opportunityWeights = null;
            if (opts.with_staying_weight || !uniqueActivities.isEmpty()) {
                opportunityWeights = LoadOpportunityWeights.load(scenarioPath.resolve("opportunity_weights.csv"), scenario);
                if (opts.normalize_weights_across_nodes) opportunityWeights = LoadOpportunityWeights.divideByMaxAcrossNodes(opportunityWeights);
            }

            new PopulationWriter(respondents, partOutputDir.resolve("respondents.tsv").toString()).write();
            new PopulationWriter(agents, partOutputDir.resolve("population.tsv").toString()).write();

            List<ActivityNode> homeNodes = ScenarioUtils.findHomes(scenario, agents);
            MultiRoundTrip<ActivityNode> initState;
            if (warmStartFile != null) {
                if (!Files.exists(Path.of(warmStartFile))) {
                    throw new FileNotFoundException("Missing warm start file " + warmStartFile);
                }
                MultiRoundTrip<ActivityNode> globalState = MultiRoundTripJsonIO.singleton().readFromFile(scenario, warmStartFile);
                initState = new MultiRoundTrip<>(agents.size());
                for (int j = 0; j < agents.size(); j++) {
                    RoundTrip<ActivityNode> globalRt = globalState.getRoundTrip(partFrom + j);
                    RoundTrip<ActivityNode> localRt = new RoundTrip<>(j, globalRt.cloneNodes(), globalRt.cloneDepartures());
                    localRt.setEpisodes(scenario.getOrCreateSimulator().simulate(localRt));
                    initState.setRoundTripAndUpdateSummaries(j, localRt);
                }
                logger.info("Partition {} warm-started from {} (global offset {})", partName, warmStartFile, partFrom);
            } else {
                initState = ScenarioUtils.createInitialMultiRoundTrip(scenario, homeNodes, agents);
            }

            // Must be registered before any weight registers its own summary: summaries are
            // updated in registration order, and both StayingSummary and ExplainSummary read
            // this one during their own update. Registering it here is also what switches the
            // feature on -- both look it up on the MultiRoundTrip and treat absence as "off".
            if (!uniqueActivities.isEmpty()) {
                UniqueActivityNodeSummary uniqueActivityNodes = new UniqueActivityNodeSummary(opportunityWeights);
                uniqueActivityNodes.addActivities(uniqueActivities.toArray(new Activity[0]));
                initState.addSummary(uniqueActivityNodes);
                initState.recomputeSummaries();
                logger.info("\tRestricting to a single (highest-opportunity) node per round trip for: {}", uniqueActivities);
            }

            List<SurveyResponse<ActivityNode>> responses = buildKernelResponses(respondents, opts);
            se.vti.roundtrips.common.Runner<ActivityNode> runner = new se.vti.roundtrips.common.Runner<>(scenario)
                .setInitialState(initState)
                .setPopulationBinomialPrior(3.125);

            BlockAverageTerminationCriterion<MultiRoundTrip<ActivityNode>> blockCrit = new BlockAverageTerminationCriterion<MultiRoundTrip<ActivityNode>>()
                    .setConvergenceStatsFileName(partOutputDir.resolve("convergenceStats.tsv").toString())
                    .setRelativeVarianceTolerance(opts.stationarity_tolerance).setStandardizedMeanTolerance(opts.stationarity_tolerance);

            if (opts.enforce_correct_home_location) runner.addIndividualWeight(new StrictlyEnforceCorrectHomeLocationWeight(homeNodes));
            if (opts.enforce_unique_home_location) runner.addIndividualWeight(new StrictlyEnforceUniqueHomeLocation());
            logger.info("\tAdded StrictlyEnforeCorrectHomeLocationWeight as individualWeight");
            runner.addIndividualWeight(new StrictlyRequireMinNumberOfStops<>(1));

            var stayingWeight = new StayingWeight(opportunityWeights, 1e-5);
            stayingWeight.configureRequiredSummary(initState);

            runner.addPopulationWeight(stayingWeight, opts.stay_mixing_weight);
            logger.info("Geometric mean staying weight added");
        
            var movementWeight = new MovementWeight(odMatrix, 1e-5);
            if (opts.raw_counts_flag) movementWeight.withRawCounts();
            movementWeight.configureRequiredSummary(initState);
            runner.addPopulationWeight(movementWeight, opts.move_mixing_weight);
            logger.info("Geometric Mean Movement weight added");
            
            ExplainRoundTripsByResponses kernelWeights = new ExplainRoundTripsByResponses(responses, agents, opts.traveler_kernel_threshold);
            runner.addPopulationWeight(kernelWeights, opts.kernel_mixing_weight);
            kernelWeights.configureRequiredSummary(initState);
            logger.info("Kernel weights added");

            MHWeightContainer<MultiRoundTrip<ActivityNode>> weightsContainer = new MHWeightContainer<>();
            for (MHWeight<MultiRoundTrip<ActivityNode>> w : runner.getSamplingWeightsView()) weightsContainer.add(w);
        
            runner.addStateProcessor(new VariablesLogger<>(effectiveMsgInterval, partOutputDir.resolve("stateVariables.tsv").toString()));
            runner.addStateProcessor(new MHWeightsToFileLogger<>(effectiveMsgInterval, weightsContainer, partOutputDir.resolve("weights.tsv").toString()));
            runner.addStateProcessor(new XMLStateLogger<>(effectiveMsgInterval, partOutputDir));
            runner.addStateProcessor(new ActivityDurationLogger<>(effectiveMsgInterval, partOutputDir.resolve("activityDurations.tsv").toString()));
            runner.addStateProcessor(new ProgressLogger<>(effectiveMsgInterval, partName));

            runner.setMessageInterval(0);

            if (opts.min_number_of_stable_samples > 0) blockCrit.setMinSamples(opts.min_number_of_stable_samples); 
            CombinedAnyTerminationCriterion<MultiRoundTrip<ActivityNode>> tc = new CombinedAnyTerminationCriterion<>(blockCrit);
            if (opts.max_number_of_iterations != null) tc.add(new FixedNumberOfIterationsTerminationCriterion<>(opts.max_number_of_iterations));
            runner.setTerminationCriterion(tc);

            logger.info("Partition {}: Starting MCMC", partName);
            runner.run();

            if (opts.write_final_state) {
                MultiRoundTripJsonIO.singleton().writeToFile(
                        runner.getFinalState(),
                        partOutputDir.resolve("finalState.json").toString());
            }


        } finally {
            ThreadContext.clearAll();
        }
    }
    
    List<SurveyResponse<ActivityNode>> buildKernelResponses(List<Person> kernelRespondents, Options opts) {
        logger.info("Kernel construction: traveler(lAge={}, lInc={}, eta={}) | nStops={} | scheduleOverlap(bw={})",
                opts.lambda_age, opts.lambda_income, opts.eta,opts.n_stops_mixing_weight > 0 ? "ON(bw=" + opts.n_stops_mixing_weight + ")" : "OFF",
                opts.schedule_overlap_mixing_weight > 0 ? "ON(bw=" + opts.schedule_overlap_mixing_weight + ")" : "OFF");

        double[] travelerParams = {opts.lambda_age, opts.lambda_income, opts.eta};
        List<SurveyResponse<ActivityNode>> responses = new ArrayList<>();

        for (Person person : kernelRespondents) {
            Kernel kTrav = new TravelerKernel(person, travelerParams);

            CompositeKernel kSched = new CompositeKernel();
            if (opts.n_stops_mixing_weight > 0) kSched.add(new NumberOfStopsKernel(person.getTrip().getStopCount(), opts.n_stops_bandwidth), opts.n_stops_mixing_weight);
            if (opts.schedule_overlap_mixing_weight > 0) {
                GaussianScheduleOverlapKernel kOverlap = new GaussianScheduleOverlapKernel(person, opts.median_durations);
                kSched.add(kOverlap, opts.schedule_overlap_mixing_weight);
            }

            CompositeKernel k = new CompositeKernel()
                .add(kTrav, CompositeKernel.Role.TRAVELER)
                .add(kSched, CompositeKernel.Role.SCHEDULE);

            k.setMixtureWeights(new double[] { opts.traveler_kernel_mixing_weight, opts.schedule_kernel_mixing_weight});
            responses.add(new SurveyResponse<>(person, k));
        }
        return responses;
    }

    static <T> List<List<T>> splitEvenly(List<T> list, int n) {
        int size = list.size();
        int base = size / n;
        int extra = size % n;
        List<List<T>> parts = new ArrayList<>(n);
        int from = 0;
        for (int i = 0; i < n; i++) {
            int to = from + base + (i < extra ? 1 : 0);
            parts.add(list.subList(from, to));
            from = to;
        }
        return parts;
    }
}