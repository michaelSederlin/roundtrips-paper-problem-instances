package renate.cases.amal;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
import picocli.CommandLine.Model.CommandSpec;

import renate.cases.CaseRunner;
import renate.cases.RunnerOptions;
import renate.kernels.activitylocationkernels.JaccardActivityLocationKernel;
import renate.kernels.commons.CompositeKernel;
import renate.kernels.commons.Features;
import renate.kernels.commons.Kernel;
import renate.kernels.commons.Role;
import renate.kernels.schedulekernels.GaussianScheduleOverlapKernel;
import renate.kernels.schedulekernels.NumberOfStopsKernel;
import renate.kernels.schedulekernels.TotalTravelTimeKernel;
import renate.kernels.travelerkernels.IndicatorTravelerKernel;
import renate.kernels.travelerkernels.TravelerKernel;
import renate.model.Activity;
import renate.model.Person;
import renate.model.SurveyResponse;
import renate.model.nodes.NodeWithActivity;
import renate.model.weights.ExplainRoundTripsByResponses;
import renate.model.weights.MovementWeight;
import renate.model.weights.StrictlyEnforceCorrectHomeLocationWeight;
import renate.model.weights.StrictlyEnforceUniqueHomeLocation;
import renate.model.weights.GeometricMeanStayingWeight;
import renate.rvu.MDDR;
import renate.rvu.MDDRRepository;
import renate.rvu.UPBD;
import renate.rvu.UPBDRepository;
import renate.stateprocessors.PopulationWriter;
import renate.utils.LoadOpportunityWeights;
import renate.utils.MatrixDataReader;
import renate.utils.ScenarioUtils;
import renate.utils.summaries.UniqueActivityNodeSummary;
import se.vti.roundtrips.common.Scenario;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.multiple.MultiRoundTripJsonIO;
import se.vti.roundtrips.samplingweights.StrictlyPeriodicSchedule;
import se.vti.roundtrips.samplingweights.StrictlyRequireMinNumberOfStops;
import se.vti.roundtrips.single.RoundTrip;
import se.vti.utils.PerThreadConsole;
import se.vti.utils.PythonRunner;
import se.vti.utils.misc.Tuple;


@Command(name = "Amal", mixinStandardHelpOptions = true)
public class AmalRunner implements Callable<Integer> {
    static final Logger logger = LogManager.getLogger(AmalRunner.class);
    static final DateTimeFormatter RUN_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    static final ZoneId RUN_TIMESTAMP_ZONE = ZoneId.of("Europe/Stockholm");

    @CommandLine.Spec CommandSpec spec; 
    @Mixin RunnerOptions opts;
    public static void main(String[] args) throws Exception {
        System.exit(new CommandLine(new AmalRunner()).execute(args));
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
        Path scenarioPath = Path.of(opts.scenarioDir);
        Path outputPath = Path.of(opts.outputDir).resolve(opts.name);
        Path runOutputPath = resolveRunOutputPath(outputPath);
        int effectiveMsgInterval = (opts.msgInterval < 0) ? (opts.maxIter != null ? opts.maxIter / 100 : 1000) : opts.msgInterval;

        Files.createDirectories(runOutputPath);
        ThreadContext.put("runId", "amal");
        ThreadContext.put("logDir", runOutputPath.toString());
        copyArgumentsFileIfPresent(runOutputPath);

        logger.info("===== AMAL Parallel Simulation setup =====");
        logger.info("--- Run ---");
        logger.info("  name={}  |  maxProcesses={}", opts.name, opts.maxProcesses > 0 ? opts.maxProcesses : "auto");
        logger.info("  maxIter={}  |  seed={}  |  msgInterval={}", opts.maxIter, opts.seed, effectiveMsgInterval);
        logger.info("  timeBinSize={}h  |  horizon={}h  |  stationarityTolerance={}", opts.timeBinSize, opts.horizon, opts.stationarity_tolerance);
        logger.info("--- Paths ---");
        logger.info("  scenarioDir:   {}", scenarioPath.toAbsolutePath());
        logger.info("  outputDir:     {}", outputPath.toAbsolutePath());
        logger.info("  runOutputDir:  {}", runOutputPath.toAbsolutePath());
        logger.info("  warmStartFile: {}", opts.warmStartFile != null ? opts.warmStartFile : "none");
        logger.info("--- Prior ---");
        logger.info("  binomialPrior={}", opts.binomialPrior);
        logger.info("--- Traveler kernel ---");
        logger.info("  lAge={}  |  lInc={}  |  eta={}  |  threshold={}  |  travelerMixWeight={}",
                opts.lAge, opts.lInc, opts.eta, opts.kernelThreshold, opts.travelerMixWeight);
        logger.info("--- Schedule kernel (withKernelWeights={}, factor={}) ---", opts.withKernelWeights, opts.kernelFactor);
        logger.info("  nStops:  bandwidth={}  |  weight={}", opts.nStopsBandwidth, opts.n_stops_weight);
        logger.info("  ttt:     bandwidth={}  |  weight={}", opts.tttBandwidth, opts.ttt_weight);
        logger.info("  overlap: bandwidth={}  |  weight={}", opts.scheduleOverlapBandwidth, opts.schedule_overlap_weight);
        logger.info("  scheduleMixWeight={}  |  subtractPartition={}", opts.schedule_mixing_weight, opts.subtractPartition);
        logger.info("--- Staying weight (withStayingWeight={}, geometricMean={}) ---",
                opts.with_staying_weight, opts.geometric_mean_staying_weight_flag);
        logger.info("  withCenterStay={}  |  stayMixingWeight={}  |  normalizeWeightsAcrossNodes={}  |  normalizeByMeanSigma={}",
                opts.with_center_stay, opts.stay_mixing_weight, opts.normalize_weights_across_nodes, opts.normalize_by_mean_sigma);
        logger.info("--- Movement weight (withMovementWeight={}) ---", opts.with_movement_weight);
        logger.info("  geometricMean={}  |  traceNormalized={}  |  moveMixingWeight={}",
                opts.geometric_mean_movement_weight, opts.trace_normalized_movement_weight, opts.move_mixing_weight);
        logger.info("--- Output ---");
        logger.info("  writeFinalState={}  |  verbose={}", opts.writeFinalState, opts.verbose);
        logger.info("--- Plotting ---");
        logger.info("  weights={}  |  convergenceStats={}  |  evaluationPlots={}  |  surveyTimeStructure={}",
                opts.plot_weights, opts.plot_convergence_stats, opts.plot_evaluation_plots, opts.plot_survey_time_structure);
        logger.info("  activityLocation={}  |  activityDurationReproduction={}  |  kernelMatrices={}  |  reproduction={}",
                opts.activity_location_plotter, opts.plot_activity_duration_reproduction, opts.plot_kernel_matrices, opts.plot_reproduction);
        logger.info("  odAndTransitions={}", opts.plot_od_and_transitions);
        logger.info("  populationReproduction={}", opts.plot_population_reproduction);
        logger.info("  individualReproduction={}", opts.plot_individual_reproduction);
        logger.info("===========================================");

        // LOAD FULL POPULATION
        UPBD upbd = new UPBDRepository().fromCSV(scenarioPath.resolve("upbd.csv")).load();
        MDDR mddr = new MDDRRepository().fromCSV(scenarioPath.resolve("mddr.csv")).load();
        List<Person> population = ScenarioUtils.loadRespondents(upbd, mddr, upbd.size());
        List<Person> completeAgents = new ArrayList<>();
        List<Person> completeRespondents = new ArrayList<>();

        for (Person p : population) {
            if ((opts.respondentSelection.wanted_respondents != null) && (!opts.respondentSelection.wanted_respondents.contains(p.getName()))) continue;
            completeRespondents.add(p);
        }

        for (Person p : population) {
            if ((opts.wanted_agents != null) && (!opts.wanted_agents.contains(p.getName()))) continue;
            completeAgents.add(p);
        }

        logger.info("Loaded {} respondents and {} agents from {} persons", completeRespondents.size(), completeAgents.size(), population.size());

        Map<Tuple<String, String>, Double> odMatrix = null;
        if (opts.with_movement_weight) {
            odMatrix = MatrixDataReader.readWithSemicolonSeparator(scenarioPath.resolve("od_matrix.tsv"));
        }

        final Map<Tuple<String, String>, Double> finalOdMatrix = odMatrix;

        final List<Person> finalCompleteRespondents;
        // List<Person> kernelRespondents = ;
        if (opts.respondentSelection.n_respondents != null) {
            List<Person> shuffled = new ArrayList<>(completeRespondents);
            Collections.shuffle(shuffled, new Random(opts.seed));
            finalCompleteRespondents = shuffled.subList(0, Math.min(opts.respondentSelection.n_respondents, shuffled.size()));
        } else {
            finalCompleteRespondents = completeRespondents;
        }
        // final List<Person> finalKernelRespondents = finalCompleteRespondents;
        logger.info("Kernel respondents: {} of {}", finalCompleteRespondents.size(), population.size());

        int nParts = (opts.maxProcesses > 0) ? opts.maxProcesses : Runtime.getRuntime().availableProcessors();
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
                            effectiveMsgInterval, partOutputDir, scenarioPath, opts.warmStartFile, partFrom);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return null;
            }));
            from += agents.size();
        }
        pool.shutdown();
        for (var f : futures) f.get();

        // if (partitions.size() > 1) {
        mergePartitions(runOutputPath);
        plotAggregateResults(runOutputPath, scenarioPath);
        // } else {
        //     logger.info("Single partition run -- skipping partition merge ({} already holds the run's output)", runOutputPath);
        // }

        return 0;
    }

    void mergePartitions(Path runOutputPath) throws IOException, InterruptedException {
        logger.info("Merging partitions into {}", runOutputPath.resolve("partitions_merged"));
        Files.createDirectories(runOutputPath.resolve("partitions_merged"));

        String[] mergeStatesArgs = new String[] {
                "--results-dir", runOutputPath.toString(),
                "--population-filename", "population.tsv",
                "--window", "10"
            };
        new PythonRunner("../ddsutils/src/main/python/ddsutils/merge_states_xml.py", mergeStatesArgs).run();

        String[] mergePopulationArgs = new String[] {
                "--results-dir", runOutputPath.toString(),
                "--population-filename", "population.tsv",
                "--target-filename", "population_merged.tsv"
            };
        new PythonRunner("../ddsutils/src/main/python/ddsutils/merge_population_csv.py", mergePopulationArgs).run();

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

    void plotAggregateResults(Path runOutputPath, Path scenarioPath) throws IOException, InterruptedException {
        List<String> aggregateArgs = new ArrayList<>(List.of(
                "--results-dir", runOutputPath.toString(),
                "--data-dir", scenarioPath.toString(),
                "--convergence-stats-filename", opts.convergenceStatsFilename,
                "--msg-interval", String.valueOf(opts.msgInterval),
                "--time-bin-size-h", String.valueOf(opts.timeBinSize),
                "--save", 
                "--output-suffix", opts.output_suffix));

        if (opts.measurementStatesXml != null) {
            aggregateArgs.add("--measurement-states-xml");
            aggregateArgs.add(opts.measurementStatesXml);
        }

        if (opts.validationStatesXml != null) {
            aggregateArgs.add("--validation-states-xml");
            aggregateArgs.add(opts.validationStatesXml);
        }

        String[] aggregatePlotArgs = aggregateArgs.toArray(new String[0]);

        if (opts.plot_population_reproduction) {
            logger.info("Plotting population-level reproduction (departures, nStops, location)");
            new PythonRunner("../ddsutils/src/main/python/ddsutils/plotting/plot_population_reproduction.py", aggregatePlotArgs).run();
        }

        if (opts.plot_od_and_transitions) {
            logger.info("Plotting OD and transition probabilities");
            new PythonRunner("../ddsutils/src/main/python/ddsutils/plotting/plot_od_and_transitions.py", aggregatePlotArgs).run();
        }

        if (opts.plot_individual_reproduction) {
            logger.info("Plotting individual population reproduction");
            new PythonRunner("../ddsutils/src/main/python/ddsutils/plotting/plot_individual_reproduction.py", aggregatePlotArgs).run();
        }

        if (opts.plot_education_diagnostics) {
            new PythonRunner("../ddsutils/src/main/python/ddsutils/plotting/plot_education_diagnostics.py", aggregatePlotArgs).run();
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

            Scenario<NodeWithActivity> scenario = new Scenario<>(new Random(opts.seed));
            scenario.setNumberOfTimeBins((int) (opts.horizon / opts.timeBinSize));
            scenario.setTimeBinSize_h(opts.timeBinSize);
            ScenarioUtils.loadNetworkFromTSV(scenario, scenarioPath);

            // Activities restricted to a single (highest-opportunity) node per round trip.
            Set<Activity> uniqueActivities = EnumSet.noneOf(Activity.class);
            for (Map.Entry<Activity, Boolean> entry : opts.unique_activity_nodes.entrySet()) {
                if (Boolean.TRUE.equals(entry.getValue())) uniqueActivities.add(entry.getKey());
            }
            if (!uniqueActivities.isEmpty() && opts.schedule_overlap_c != null) {
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

            Map<NodeWithActivity, Double> opportunityWeights = null;
            if (opts.with_staying_weight || opts.geometric_mean_staying_weight_flag || !uniqueActivities.isEmpty()) {
                opportunityWeights = LoadOpportunityWeights.load(scenarioPath.resolve("opportunity_weights.csv"), scenario);
                if (opts.normalize_weights_across_nodes) opportunityWeights = LoadOpportunityWeights.divideByMaxAcrossNodes(opportunityWeights);
                if (opts.normalize_by_mean_sigma) opportunityWeights = LoadOpportunityWeights.normalizeByMeanSigma(opportunityWeights);
            }

            new PopulationWriter(respondents, partOutputDir.resolve("respondents.tsv").toString()).write();
            new PopulationWriter(agents, partOutputDir.resolve("population.tsv").toString()).write();

            List<NodeWithActivity> homeNodes = ScenarioUtils.findHomes(scenario, agents);
            MultiRoundTrip<NodeWithActivity> initState;
            if (warmStartFile != null) {
                if (!Files.exists(Path.of(warmStartFile))) {
                    throw new FileNotFoundException("Missing warm start file " + warmStartFile);
                }
                MultiRoundTrip<NodeWithActivity> globalState =
                        MultiRoundTripJsonIO.singleton().readFromFile(scenario, warmStartFile);
                initState = new MultiRoundTrip<>(agents.size());
                for (int j = 0; j < agents.size(); j++) {
                    RoundTrip<NodeWithActivity> globalRt = globalState.getRoundTrip(partFrom + j);
                    RoundTrip<NodeWithActivity> localRt =
                            new RoundTrip<>(j, globalRt.cloneNodes(), globalRt.cloneDepartures());
                    localRt.setEpisodes(scenario.getOrCreateSimulator().simulate(localRt));
                    initState.setRoundTripAndUpdateSummaries(j, localRt);
                }
                logger.info("Partition {} warm-started from {} (global offset {})", partName, warmStartFile, partFrom);
            } else {
                initState = ScenarioUtils.createInitialMultiRoundTrip(scenario, homeNodes, agents);
            }

            // Must be registered before any weight registers its own summary: summaries are
            // updated in registration order, and both StayingSummary and KernelSummary read
            // this one during their own update. Registering it here is also what switches the
            // feature on -- both look it up on the MultiRoundTrip and treat absence as "off".
            if (!uniqueActivities.isEmpty()) {
                UniqueActivityNodeSummary uniqueActivityNodes = new UniqueActivityNodeSummary(opportunityWeights);
                uniqueActivityNodes.addActivities(uniqueActivities.toArray(new Activity[0]));
                initState.addSummary(uniqueActivityNodes);
                initState.recomputeSummaries();
                logger.info("\tRestricting to a single (highest-opportunity) node per round trip for: {}", uniqueActivities);
            }

            List<SurveyResponse<NodeWithActivity>> responses = buildKernelResponses(respondents, opts);
            CaseRunner runner = new CaseRunner(scenario, partName)
                    .setInitialState(initState)
                    .withDefaultLoggers(partOutputDir, effectiveMsgInterval, partName)
                    .withBlockConvergence(opts.stationarity_tolerance, opts.min_number_of_stable_samples);

            // ADDING DEFAULT PRIORS
            logger.info("Partition {}: Adding weights:", partName); 
            if (opts.binomialPrior) {
                runner.setPopulationBinomialPrior(3.125);
            } else {
                runner.setUniformPrior();
            }

            if (opts.strictly_periodic_schedule) runner.addIndividualWeight(new StrictlyPeriodicSchedule<>(scenario.getPeriodLength_h()));
            logger.info("\tadded StrictlyPeriodSchedule({}) as individualWeight", scenario.getPeriodLength_h());
            if (opts.enforceCorrectHomeLocation) runner.addIndividualWeight(new StrictlyEnforceCorrectHomeLocationWeight(homeNodes));
            if (opts.enforce_unique_home_location) runner.addIndividualWeight(new StrictlyEnforceUniqueHomeLocation());
            logger.info("\tAdded StrictlyEnforeCorrectHomeLocationWeight as individualWeight");
            runner.addIndividualWeight(new StrictlyRequireMinNumberOfStops<>(1));

            if (opts.with_staying_weight) {
                var geometricMeanStayingWeight = new GeometricMeanStayingWeight(opportunityWeights, 1e-5)
                    .withExponent(opts.staying_weight_exponent);
                geometricMeanStayingWeight.configureRequiredSummary(initState);

                // var stayingWeight = new StayingWeight(opportunityWeights);
                // stayingWeight.setC(-Math.log(LoadOpportunityWeights.meanSigma(opportunityWeights)));

                runner.addPopulationWeight(geometricMeanStayingWeight, opts.stay_mixing_weight);
                logger.info("Geometric mean staying weight added");
            }
            
            if (opts.with_movement_weight) {
                var movementWeight = new MovementWeight(odMatrix, 1e-5)
                    .withExponent(opts.movement_weight_exponent)
                    .withGeometricMean();
                if (opts.raw_counts_flag) movementWeight.withRawCounts();
                movementWeight.configureRequiredSummary(initState);
                runner.addPopulationWeight(movementWeight, opts.move_mixing_weight);
                logger.info("Geometric Mean Movement weight added");
            }
            
            if (opts.withKernelWeights) {
                ExplainRoundTripsByResponses kernelWeights =
                new ExplainRoundTripsByResponses(responses, agents, opts.kernelThreshold);
                runner.addPopulationWeight(kernelWeights, opts.kernelFactor);
                kernelWeights.configureRequiredSummary(initState);
                logger.info("Kernel weights added");
            }
            
            if (opts.maxIter != null) runner.withMaxIter(opts.maxIter);
            if (opts.verbose) runner.verbose(); else runner.setMessageInterval(0);
            runner.run();

            if (opts.writeFinalState) {
                MultiRoundTripJsonIO.singleton().writeToFile(
                        runner.getFinalState(),
                        partOutputDir.resolve("finalState.json").toString());
            }
            String[] plotArgs = new String[] {
                    "--results-dir", partOutputDir.toString(),
                    "--states-xml", partOutputDir.resolve("states.xml").toString(),
                    "--data-dir", scenarioPath.toString(),
                    "--window", "15",
                    "--save", 
                    "--output-suffix", opts.output_suffix
                };

        if (opts.plot_weights) { logger.info("Partition {} plotting weights", partName); new PythonRunner("../ddsutils/src/main/python/ddsutils/plotting/plot_weights.py", plotArgs).run(); }
        if (opts.plot_convergence_stats) { logger.info("Partition {} plotting convergence stats", partName); new PythonRunner("../ddsutils/src/main/python/ddsutils/plotting/plot_convergence_stats.py", plotArgs).run(); }
        if (opts.plot_evaluation_plots) { logger.info("Partition {} plotting evaluation plots", partName); new PythonRunner("../ddsutils/src/main/python/ddsutils/plotting/plot_evaluation_plots.py", plotArgs).run(); }
        if (opts.plot_survey_time_structure) { logger.info("Partition {} plotting survey time structure", partName); new PythonRunner("../ddsutils/src/main/python/ddsutils/plotting/plot_survey_time_structure.py", plotArgs).run(); }
        if (opts.activity_location_plotter) { logger.info("Partition {} plotting activity locations", partName); new PythonRunner("../ddsutils/src/main/python/ddsutils/plotting/activity_location_plotter.py", plotArgs).run(); }
        if (opts.plot_activity_duration_reproduction) { logger.info("Partition {} plotting activity duration reproduction", partName); new PythonRunner("../ddsutils/src/main/python/ddsutils/plotting/plot_activity_duration_reproduction.py", plotArgs).run(); }
        if (opts.plot_kernel_matrices) { logger.info("Partition {} plotting kernel matrices", partName); new PythonRunner("../ddsutils/src/main/python/ddsutils/plotting/plot_kernel_matrices.py", plotArgs).run(); }
        if (opts.plot_reproduction) { logger.info("Partition {} plotting reproduction", partName); new PythonRunner("../ddsutils/src/main/python/ddsutils/plotting/plot_reproduction.py", plotArgs).run(); }

        } finally {
            ThreadContext.clearAll();
        }
    }
    
    List<SurveyResponse<NodeWithActivity>> buildKernelResponses(List<Person> kernelRespondents, RunnerOptions opts) {
        logger.info("Kernel construction: traveler(lAge={}, lInc={}, eta={}) | "
                + "nStops={} | ttt={} | scheduleOverlap(bw={})",
                opts.lAge, opts.lInc, opts.eta,
                opts.n_stops_weight > 0 ? "ON(bw=" + opts.n_stops_weight + ")" : "OFF",
                opts.ttt_weight > 0 ? "ON(bw=" + opts.ttt_weight + ")" : "OFF",
                opts.schedule_overlap_weight > 0 ? "ON(bw=" + opts.schedule_overlap_weight + ")" : "OFF");

        double[] travelerParams = {opts.lAge, opts.lInc, opts.eta};
        List<SurveyResponse<NodeWithActivity>> responses = new ArrayList<>();
        for (Person person : kernelRespondents) {
            Kernel<? extends Features> kTrav = opts.indicatorTravelerKernel 
                ? new IndicatorTravelerKernel(person)
                : new TravelerKernel(person, travelerParams);  // Default is TravelerKernel

            CompositeKernel kSched = new CompositeKernel();
            if (opts.n_stops_weight > 0) kSched.add(new NumberOfStopsKernel(person.getTrip().getStopCount(), opts.nStopsBandwidth), opts.n_stops_weight);
            if (opts.ttt_weight > 0) kSched.add(new TotalTravelTimeKernel(person.getTrip().getTotalTravelTime_h(), opts.tttBandwidth), opts.ttt_weight);
            if (opts.schedule_overlap_weight > 0) {
                GaussianScheduleOverlapKernel kOverlap = (opts.schedule_overlap_c != null)
                    ? new GaussianScheduleOverlapKernel(person, opts.schedule_overlap_c, opts.median_durations)
                    : new GaussianScheduleOverlapKernel(person, opts.scheduleOverlapBandwidth);

                kSched.add(kOverlap, opts.schedule_overlap_weight);
            }

            CompositeKernel kloc = new CompositeKernel();
            if (opts.jaccard_activity_location_kernel) {
                kloc.add(new JaccardActivityLocationKernel(person, opts.jaccard_activity_location_kernel_bandwidth));
            }

            CompositeKernel k = new CompositeKernel()
                    .add(kTrav, Role.TRAVELER)
                    .add(kSched, Role.SCHEDULE)
                    .add(kloc, Role.LOCATION)
                    ;
            k.setMixtureWeights(new double[] { opts.travelerMixWeight, opts.schedule_mixing_weight, opts.location_kernel_weight});
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