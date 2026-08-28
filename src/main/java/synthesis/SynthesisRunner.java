
package synthesis;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;


import se.vti.roundtrips.common.Runner;
import se.vti.roundtrips.common.Scenario;
import common.Activity;
import common.GridNodeWithActivity;
import common.logging.*;
import common.IterationCounter;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.multiple.MultiRoundTripJsonIO;
import se.vti.roundtrips.samplingweights.timeUse.LogarithmicTimeMultiplePersonsSingleDay;
import se.vti.roundtrips.simulator.DefaultSimulator;
import se.vti.roundtrips.single.RoundTrip;
import common.PerThreadConsole;
import se.vti.utils.misc.metropolishastings.*;
import se.vti.utils.misc.metropolishastings.terminationcriteria.BlockAverageTerminationCriterion;

@Command(name = "synthesis", mixinStandardHelpOptions = true )    
public class SynthesisRunner implements Callable<Integer> {
	static final int DEFAULT_MSG_INTERVAL = 200_000;
		   
    public static Logger logger = LogManager.getLogger(SynthesisRunner.class);
	@Mixin private Options opts = new Options();
    public static void main(String[] args) throws Exception {
		int exitCode = new CommandLine(new SynthesisRunner()).execute(args);
		System.exit(exitCode);
	}

	@Override
	public Integer call() throws Exception {
        Files.createDirectories(opts.output_dir);
        opts.save();
        int msgInt = (opts.max_iter >= DEFAULT_MSG_INTERVAL) ? DEFAULT_MSG_INTERVAL : Math.max(1, opts.max_iter / 100);

        
        // ------------ Threads ------------ 
        int availProcs = Runtime.getRuntime().availableProcessors();
        int numProc = (opts.max_processes <= 0) ?
            availProcs : 
            Math.min(availProcs, opts.max_processes);

        int basePopulationSize = opts.population_size / numProc;
        int remainderPopulationSize = opts.population_size % numProc;

        List<Integer> partitionsSeeds = new ArrayList<>(numProc);
        
        List<Integer> populationPartitionsSizes = new ArrayList<>(numProc);
        List<Scenario<GridNodeWithActivity>> scenarios = new ArrayList<>(numProc);
        List<GridWorld> gridWorlds = new ArrayList<>(numProc);

        for (int ii = 0; ii < numProc; ii++) {
            partitionsSeeds.add(opts.base_seed + ii * 49);
            populationPartitionsSizes.add(basePopulationSize + (ii < remainderPopulationSize ? 1 : 0));
            Scenario<GridNodeWithActivity> scenario = new Scenario<>();
            scenario.getRandom().setSeed(opts.scenario_seed);
            scenario.setNumberOfTimeBins((int) (opts.horizon / opts.time_bin_size));
            scenario.setTimeBinSize_h(opts.time_bin_size);
            scenarios.add(scenario);    
            
            gridWorlds.add(GridWorld.createDefault(scenario, opts.grid_size, new Random(opts.grid_world_seed)));     
        }

        writeNetworkTsv(opts.output_dir, scenarios.get(0));
        Map<GridNodeWithActivity, Integer> nodeOppos = gridWorlds.get(0).getNodeOpportunities();
        writeNodeOpportunitiesToCSV(opts.opportunities_path, nodeOppos);

        // ------------ Main Setup Logging ------------ 
        ThreadContext.put("runId", "main");
        ThreadContext.put("logDir", opts.output_dir.toString());

        logger.info("===== Simulation setup =====");
        logger.info("Start time: {}", LocalDateTime.now());
        logger.info("Maximum number of iterations {}:", opts.max_iter);
        logger.info("Message interval {}:", msgInt);

        // --- Parsed arguments ---
//      logger.info("Args: N={}, M={}, nIter={}", N, M, maxIter);
        logger.info("Target population size: ", opts.population_size);

        // --- Paths ---
//      logger.info("Env root:         {}", root);
        logger.info("Base output dir:  {}", opts.output_dir);
        logger.info("Preferences file: {}", opts.preferences_path);
        logger.info("Opportunities:    {}", opts.opportunities_path);

        // --- Parallelism / partitioning ---
        logger.info("Available processors (numProc): {}", numProc);
        logger.info("Base partition size: {}, remainder: {}", basePopulationSize, remainderPopulationSize);
        logger.info("Population sizes per partition: {}", populationPartitionsSizes);
        logger.info("Seeds per partition: {} (baseSeed={})", partitionsSeeds, opts.base_seed);

        // --- Scenario settings ---
        logger.info("Grid size: {}x{}", opts.grid_size, opts.grid_size);
        logger.info("Period length (h): {}", opts.horizon);
        logger.info("Time bin size (h): {}", opts.time_bin_size);
        logger.info("Number of time bins: {}", (int) (opts.horizon / opts.time_bin_size));

        logger.info("Work opening hours: ({}, {})", opts.work_open, opts.work_close);
        logger.info("EDUCATION opening hours: ({}, {})", opts.education_open, opts.education_close);
        logger.info("OTHER opening hours: ({}, {})", opts.other_open, opts.other_close);
        logger.info("Minimum home duration opening hours: {}", opts.min_home_duration);
        // logger.info("Maximum opening hour shift: {}", potentialShiftMagnitude);

        logger.info("Saved opportunity weights to: {}", opts.opportunities_path);
        logger.info("============================");
        
        
        // ------------ Run threads ------------ 
        ExecutorService pool = Executors.newFixedThreadPool(numProc);
        List<Future<?>> futures = new ArrayList<>();

        PerThreadConsole.install();

        for (int ii = 0; ii < numProc; ii++) {
            final int idx = ii;
            int finalNIter = opts.max_iter;
            futures.add(pool.submit(() -> {
                try {
                    run(
                            String.valueOf(idx), partitionsSeeds.get(idx), opts.scenario_seed + idx,
                            scenarios.get(idx), gridWorlds.get(idx),
                            populationPartitionsSizes.get(idx),
                            opts, 
                            msgInt
                            );
                } finally {
                    ThreadContext.put("runId", "main");
                    ThreadContext.put("logDir", opts.output_dir.toString());
                    logger.info("Partition {} finished!", idx);
                    ThreadContext.clearAll();
                }
                return null;
            }));
        }
        pool.shutdown();
        for (var f : futures) f.get();

        return 0;
    }

    // ----------------- Static run method  -----------------
    static void run(
            String partition, long partitionSeed, int activityLocationSeed,
            Scenario<GridNodeWithActivity> scenario, GridWorld gridWorld,
            int partitionPopulationSize,
            Options opts,
            int msgInt
            ) throws Exception {
        Path partitionOutputDir = opts.output_dir.resolve(partition);
        Files.createDirectories(partitionOutputDir);
        ThreadContext.put("runId", partition);
        ThreadContext.put("logDir", partitionOutputDir.getParent().toString());

        logger.info("Starting partition {} from thread {}", partition, Thread.currentThread().getName());
        Random partitionRNG = new Random(partitionSeed);
        Random locationRNG = new Random(activityLocationSeed);
        Path partitionPath = partitionOutputDir.resolve("population.csv");

        
        // ----------------- Setup the population & compute Time-use preferences-----------------
        StandardSyntheticPopulation population = new StandardSyntheticPopulation(partitionPopulationSize, partitionSeed).identifyUniqueGroups();   
        logger.info("Partition {}: Loaded population of size {}", partition, partitionPopulationSize);

        TimeUsePreferences prefs = new TimeUsePreferences(opts.preferences_path, partitionRNG).setUniformRandomMixing(true);
        prefs.computeAndSetPreferences(population);
        prefs.moveWorkHoursOfElderlyToHome(population);

        population.writePopulation(partitionPath);

        LogarithmicTimeMultiplePersonsSingleDay<GridNodeWithActivity> timeUseWeight =
                new LogarithmicTimeMultiplePersonsSingleDay<>(scenario.getPeriodLength_h(), partitionPopulationSize);

        List<Set<GridNodeWithActivity>> homeLocationsPerPerson = new ArrayList<>(partitionPopulationSize);

        logger.info("Partition {}: Sampling activity locations for {} persons", partition, partitionPopulationSize);
        for (SyntheticPerson person : population.getPopulation()) {
            double shift = 0;

            {
                var component = timeUseWeight.createComponent(person.getHomePreference(), person.getIdx());
                var emptyComponent = timeUseWeight.createComponent(0.0, person.getIdx());
                Set<GridNodeWithActivity> homeSet = gridWorld.sampleActivityLocations(Activity.HOME, locationRNG);
                homeLocationsPerPerson.add(homeSet);
                for (GridNodeWithActivity node : scenario.getNodesView()) {
                    if (homeSet.contains(node)) {
                        component.addObservedNode(node);
                    } else if (Activity.HOME.equals(node.getActivity())) {
                        emptyComponent.addObservedNode(node);
                    }
                }
                component.setMinEnBlockDurationAtLeastOnce_h(opts.min_home_duration);
                timeUseWeight.addConfiguredComponent(component);
                timeUseWeight.addConfiguredComponent(emptyComponent);
                logger.debug("Person {} activity {} prefs: {} component: {}",
                        person.getIdx(), Activity.HOME, person.getHomePreference(), component);
            }
            {
                var component = timeUseWeight.createComponent(person.getWorkPreference(), person.getIdx());
                var emptyComponent = timeUseWeight.createComponent(0.0, person.getIdx());
                Set<GridNodeWithActivity> workSet = gridWorld.sampleActivityLocations(Activity.WORK, locationRNG);
                for (GridNodeWithActivity node : scenario.getNodesView()) {
                    if (workSet.contains(node)) {
                        component.addObservedNode(node);
                    } else if (Activity.WORK.equals(node.getActivity())) {
                        emptyComponent.addObservedNode(node);
                    }
                }
                component.setOpeningTimes_h(opts.work_open, opts.work_close);
                timeUseWeight.addConfiguredComponent(component);
                timeUseWeight.addConfiguredComponent(emptyComponent);
                logger.debug("Person {} activity {} prefs: {} component {}",
                        person.getIdx(), Activity.WORK, person.getWorkPreference(), component);
            }
            {
                var component = timeUseWeight.createComponent(person.getEducationPreference(), person.getIdx());
                var emptyComponent = timeUseWeight.createComponent(0.0, person.getIdx());
                Set<GridNodeWithActivity> educationSet = gridWorld.sampleActivityLocations(Activity.EDUCATION, locationRNG);
                for (GridNodeWithActivity node : scenario.getNodesView()) {
                    if (educationSet.contains(node)) {
                        component.addObservedNode(node);
                    } else if (Activity.EDUCATION.equals(node.getActivity())) {
                        emptyComponent.addObservedNode(node);
                    }
                }
                component.setOpeningTimes_h(opts.education_open, opts.education_close);
                timeUseWeight.addConfiguredComponent(component);
                timeUseWeight.addConfiguredComponent(emptyComponent);
                logger.debug("Person {} activity {} prefs: {} component {}",
                        person.getIdx(), Activity.EDUCATION, person.getEducationPreference(), component);
            }
            {
                var component = timeUseWeight.createComponent(person.getOtherPreference(), person.getIdx());
                var emptyComponent = timeUseWeight.createComponent(0.0, person.getIdx());
                Set<GridNodeWithActivity> otherSet = gridWorld.sampleActivityLocations(Activity.OTHER, locationRNG);
                for (GridNodeWithActivity node : scenario.getNodesView()) {
                    if (otherSet.contains(node)) {
                        component.addObservedNode(node);
                    } else if (Activity.OTHER.equals(node.getActivity())) {
                        emptyComponent.addObservedNode(node);
                    }
                }
                component.setOpeningTimes_h(opts.other_open, opts.other_close);
                timeUseWeight.addConfiguredComponent(component);
                timeUseWeight.addConfiguredComponent(emptyComponent);
                logger.debug("Person {} activity {} prefs: {} component {}",
                        person.getIdx(), Activity.OTHER, person.getOtherPreference(), component);
            }
        }

        // ----------------- Set up mh -----------------
        MultiRoundTrip<GridNodeWithActivity> initState = createInitialMultiRoundTrip(scenario, homeLocationsPerPerson, partitionPopulationSize);

        logger.info("Partition {}: Initializing runner...", partition);

        Runner<GridNodeWithActivity> runner = new Runner<>(scenario)
                .setInitialState(initState).setMessageInterval(msgInt);

        logger.info("Partition {}: Adding weights:", partition);
        if (opts.enforce_correct_home_location) runner.addIndividualWeight(new StrictlyEnforceCorrectHomeLocationWeight<>(homeLocationsPerPerson));
        runner.setPopulationUniformPrior();
        if (opts.time_use_mixing_weight > 0) runner.addPopulationWeight(timeUseWeight, opts.time_use_mixing_weight);

        MHWeightContainer<MultiRoundTrip<GridNodeWithActivity>> weights =  new MHWeightContainer<>();
        for (MHWeight<MultiRoundTrip<GridNodeWithActivity>> w : runner.getSamplingWeightsView()) weights.add(w);

        runner.addStateProcessor(new MHWeightsToFileLogger<>(msgInt, weights, partitionOutputDir.resolve("weights.tsv").toString()));
        runner.addStateProcessor(new XMLStateLogger<>(msgInt, partitionOutputDir.resolve("states.xml")));
        runner.addStateProcessor(new ActivityDurationLogger(msgInt, partitionOutputDir.resolve("activityDurations.xml")));
        runner.addStateProcessor(new ActivityDepartureDurationLogger(msgInt, partitionOutputDir.resolve("activityDeparturesDurations.xml").toString()));
        runner.addStateProcessor(new VariablesLogger(msgInt, partitionOutputDir.resolve("stateVariables.tsv")));
        runner.addStateProcessor(new SimpleStatsLogger<>(scenario, msgInt, partitionOutputDir));
        runner.addStateProcessor(new MHAbstractStateProcessor<MultiRoundTrip<GridNodeWithActivity>>(0, msgInt) {
            private long iter = 0;
            private final long  t0 = System.currentTimeMillis();
            @Override
            public void processStateHook(MultiRoundTrip<GridNodeWithActivity> state, double logWeight) {
                iter += msgInt;
                double rate = iter / Math.max(1e-9, (System.currentTimeMillis() - t0) / 1000.0);
                logger.info("Partition {}: iter={} logWeight={} ({} it/s)",
                        partition, iter, logWeight, String.format("%.0f", rate));
            }
        });

        var iterCounter = new IterationCounter<MultiRoundTrip<GridNodeWithActivity>>();
        runner.addStateProcessor(iterCounter);

        var terminationCriterion = new BlockAverageTerminationCriterion<MultiRoundTrip<GridNodeWithActivity>>()
                .setCheckInterval(10_000).setMinSamples(10_000)
                .setRelativeVarianceTolerance(opts.variance_tolerance).setStandardizedMeanTolerance(opts.mean_tolerance)
                .setConvergenceStatsFileName(partitionOutputDir.resolve("convergenceStats.tsv").toString());
        runner.setTerminationCriterion(terminationCriterion);

        logger.info("Partition {}: Starting MCMC", partition);
        runner.run();
            
        MultiRoundTripJsonIO.singleton().writeToFile(runner.getFinalState(), partitionOutputDir.resolve("final_state.json").toString());
        logger.info("Partition {}: Wrote final state to file {}", partition, partitionOutputDir.resolve("final_state.json"));
        logger.info("Partition {}: Simulation finished. Plotting results ...", partition);

        logger.info("Done!");
    }
    
    
    // ----------------- Other Static methods  -----------------

    public static void writeNetworkTsv(Path outputDir, Scenario<GridNodeWithActivity> scenario) throws Exception {
        try (PrintWriter edgeWriter = new PrintWriter(outputDir.resolve("edges.tsv").toString());
                PrintWriter nodeWriter = new PrintWriter(outputDir.resolve("nodes.tsv").toString())){
            nodeWriter.println("basicName\tname\tlabels\trow\tcol");
            edgeWriter.println("from\tto\ttt\tdist");
            for (GridNodeWithActivity n0 : scenario.getNodesView()) {
                nodeWriter.println(n0.getBasicName() + "\t" + n0.getName() + "\t" + n0.getLabels() + "\t" + n0.row + "\t" + n0.column);
                for (GridNodeWithActivity n1 : scenario.getNodesView()) {
                    double tt = scenario.getTime_h(n0, n1);
                    double dist = scenario.getDistance_km(n0, n1);
                    edgeWriter.println(n0 + "\t" + n1 + "\t" + tt + "\t" + dist);
                }
            }
        }
    }

    public static void writeNodeOpportunitiesToCSV(Path filepath, Map<GridNodeWithActivity, Integer> nodeOpportunities) throws Exception {
        try (PrintWriter writer = new PrintWriter(filepath.toString())) {
            writer.println("samscode,ANDAMAL,area_share,area");
            int total = nodeOpportunities.values().stream().mapToInt(Integer::intValue).sum();
            for (Map.Entry<GridNodeWithActivity, Integer> entry : nodeOpportunities.entrySet()) {
                GridNodeWithActivity node = entry.getKey();
                int weight = entry.getValue();
                double areaShare = (double) weight / total;
                writer.println(String.format("\"%s\",\"%s\",%.16f,%d",
                        node.getBasicName(),
                        node.getActivity().toString(),
                        areaShare,
                        weight
                        ));
            }
        }
    }

    public static MultiRoundTrip<GridNodeWithActivity> createInitialMultiRoundTrip(Scenario<GridNodeWithActivity> scenario, List<Set<GridNodeWithActivity>> homes, int n) {
        MultiRoundTrip<GridNodeWithActivity> initMrt = new MultiRoundTrip<>(n);
        for (int ii = 0; ii < n; ii++) {
            GridNodeWithActivity home = homes.get(ii).iterator().next();
            RoundTrip<GridNodeWithActivity> rt = new RoundTrip<>(ii, List.of(home), List.of(0));
            rt.setEpisodes(scenario.getOrCreateSimulator().simulate(rt));
            initMrt.setRoundTripAndUpdateSummaries(ii, rt);
        }
        return initMrt;
    }

    public static MultiRoundTrip<GridNodeWithActivity> loadInitialMRT(Scenario<GridNodeWithActivity> scenario, Path fp) throws Exception {
        try {
            MultiRoundTrip<GridNodeWithActivity> mrt = MultiRoundTripJsonIO.singleton().readFromFile(scenario, fp.toString());
            mrt.simulateAll(new DefaultSimulator<>(scenario));
            mrt.recomputeSummaries();
            logger.info("Loaded initial state from file " + fp.toString());
            return mrt;
        } catch (Exception e) {
            return null;
        }
    }
}
