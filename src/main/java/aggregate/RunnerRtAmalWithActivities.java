package aggregate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;

import aggregate.model.DDHomeSummaryWithActivities;
import aggregate.model.DDWorkByGroupSummaryWithActivities;
import aggregate.model.ODSummaryIgnoringActivities;
import aggregate.utils.IterationCounter;
import aggregate.utils.LocationReader;
import aggregate.utils.MatrixDataReader;
import aggregate.utils.PythonRunner;
import aggregate.utils.SettingsLogger;
import aggregate.utils.SimpleStatsLogger;
import aggregate.utils.XMLCompressor;
import aggregate.utils.XMLStateLogger;

import se.vti.utils.misc.Tuple;
import se.vti.utils.misc.metropolishastings.terminationcriteria.BlockAverageTerminationCriterion;

import se.vti.roundtrips.common.Node;
import se.vti.roundtrips.common.RandomRoundTripGenerator;
import se.vti.roundtrips.common.ScenarioBuilder;
import se.vti.roundtrips.examples.activityExpandedGridNetwork.Activity;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.multiple.MultiRoundTripJsonIO;
import se.vti.roundtrips.samplingweights.SingleToMultiWeight;
import se.vti.roundtrips.samplingweights.StrictlyPeriodicSchedule;
import se.vti.roundtrips.samplingweights.priors.PopulationBinomialPrior;



/**
 * Activity-specific modification:
 * 
 * Builds a network with activity-labeled nodes. All locations allow for all
 * activities.
 * 
 * Using TargetDeviationRunnerWithActivities, DDHomeSummaryWithActivities and
 * DDWorkByGroupSummaryWithActivities.
 * 
 * 
 */

public class RunnerRtAmalWithActivities {

	public static void main(String[] args) throws IOException, InterruptedException {

		
		
		// ------------------ Set Start Time ------------------

		LocalDateTime startTime = LocalDateTime.now();

		
		
		// ---- Fields -----

		long seed = 4711l;
		String inputFolder = "./data/aggregated_calibration/measurement/";
		String outputFolder = "./data/aggregated_calibration/calibration/testing/";

		int nTimeBins = 24;
		double timeBinSize_h = 1;
		int realPopulationSize = 1000;
		int nRoundTrips = 1000;

		boolean gauss = true;
		boolean discretizationNoise = true;
		double sigma = 1;

		boolean convergenceCriterion = false;
		double tolerance = 0.3;
		long nIterations = 1 * 100 * 1000;
		long msgInterval = 5000;

//		nIterations = 1 * 100 * 1000;
//		msgInterval = nIterations/200;

		
		
		// ----- Scenario Builder -----

		var scenarioBuilder = new ScenarioBuilder<Node>().setRandom(new Random(seed)).setNumberOfTimeBins(nTimeBins)
				.setTimeBinSize_h(timeBinSize_h);

		
		
		// ----- Network -----

		LocationReader locationReader = new LocationReader(inputFolder + "25_zoneNames.csv");
		String[] zones = locationReader.getLocationNames();

		for (String zone : zones) {
			for (Activity activity : Activity.values()) {
				scenarioBuilder.addNode(new Node(zone, activity));
			}
		}

		Map<Tuple<String, String>, Double> travelTime = MatrixDataReader
				.readWithSeparator(inputFolder + "25_skim_h.csv", ";");
		Map<Tuple<String, String>, Double> travelDistance = MatrixDataReader
				.readWithSeparator(inputFolder + "25_skim_km.csv", ";");

		scenarioBuilder.setMoveDistanceFunction((from, to) -> {
			Tuple<String, String> key = new Tuple<>(from.getName(), to.getName());
			return travelDistance.getOrDefault(key, 0.0);
		});

		scenarioBuilder.setMoveTimeFunction(
				(from, to) -> travelTime.getOrDefault(new Tuple<>(from.getName(), to.getName()), 0.0));

		
		
		// ------ Scenario ------

		var scenario = scenarioBuilder.build();
		var targetDeviationRunner = new TargetDeviationRunnerWithActivities(scenario);
		targetDeviationRunner.setRealPopulationSize(realPopulationSize);
		targetDeviationRunner.setNRoundTrips(nRoundTrips);
		targetDeviationRunner.setGauss(gauss);
		targetDeviationRunner.setDiscretizationNoise(discretizationNoise);
		targetDeviationRunner.setMsgInterval(msgInterval);

		
		
		// ---- Weights -----

		StrictlyPeriodicSchedule<Node> strictlyPeriodicSchedule = new StrictlyPeriodicSchedule<>(
				scenario.getPeriodLength_h());
		targetDeviationRunner.addPopulationWeight(new SingleToMultiWeight<>(strictlyPeriodicSchedule));

		targetDeviationRunner.setPopulationPrior(new PopulationBinomialPrior<>(scenario, 3.125));
//		targetDeviationRunner.setUniformPrior();
//		targetDeviationRunner.setNstopsTarget(sigma, inputFolder + "25_nStops.csv", outputFolder);

		targetDeviationRunner.setOdTarget(sigma, inputFolder + "25_od.csv", outputFolder);

//		var groupsSecondLargest = Set.of("16-17");
		targetDeviationRunner.createPopulatinGrouping(inputFolder + "25_");
		targetDeviationRunner.setHlTarget(sigma, outputFolder);
		targetDeviationRunner.setWlTarget(sigma, outputFolder);

		targetDeviationRunner.setDdHomeTarget(sigma, inputFolder + "25_ddHome.csv", outputFolder);
		targetDeviationRunner.setDdWorkTarget(sigma, inputFolder + "25_ddWork.csv", outputFolder);

		
		
		// ------ InitState ----

		MultiRoundTrip<Node> initialState = new MultiRoundTrip<>(nRoundTrips);
		initialState.addSummary(new ODSummaryIgnoringActivities<>());
		initialState.addSummary(new DDHomeSummaryWithActivities());
		initialState.addSummary(new DDWorkByGroupSummaryWithActivities(targetDeviationRunner.getPopulationGrouping(),
				targetDeviationRunner.getWorkGroup2zone2target().keySet()));
//		initialState.addSummary(new DDWorkByGroupSummarySecondLongest(targetDeviationRunner.getPopulationGrouping(),
//				targetDeviationRunner.getWorkGroup2zone2target().keySet()));

		var generator = new RandomRoundTripGenerator<Node>(scenario).setNumberOfStayEpisodesInterval(2, 2)
				.setFeasibilityCheck(r -> (!Double.isInfinite(strictlyPeriodicSchedule.logWeight(r))));
		generator.populateRandomly(initialState);
		targetDeviationRunner.setInitialState(initialState);

		
		
		// ----- StateProcessors -----

		targetDeviationRunner.configureWeightLogging("./output/logWeights.log", msgInterval);
		targetDeviationRunner.addStateProcessor(new SimpleStatsLogger<>(scenario, msgInterval, outputFolder));
		targetDeviationRunner.addStateProcessor(new XMLStateLogger<>(msgInterval, "./output/states.xml"));
		var iterCounter = new IterationCounter<MultiRoundTrip<Node>>();
		targetDeviationRunner.addStateProcessor(iterCounter);

		
		
		// ------ Run MH -------

		if (convergenceCriterion) {
			var terminationCriterion = new BlockAverageTerminationCriterion<MultiRoundTrip<Node>>()
					.setCheckInterval((int) msgInterval).setMinSamples((int) msgInterval)
					.setConvergenceStatsFileName("./output/convergenceStats.tsv")
					.setRelativeVarianceTolerance(tolerance).setStandardizedMeanTolerance(tolerance);
			targetDeviationRunner.setTerminationCriterion(terminationCriterion);
		} else {
			targetDeviationRunner.setNumberOfIterations(nIterations);
		}
		targetDeviationRunner.run();

		
		
		// ----- Log final state -----

		var finalState = targetDeviationRunner.getFinalState();
		XMLStateLogger.writeFinalState("./output/states.xml", finalState, iterCounter.getLastIteration());
		XMLCompressor.compressXML("./output/states.xml", 5, false);
		MultiRoundTripJsonIO.singleton().writeToFile(finalState, "./output/final_state.json");

		
		
		// ----- Log Settings -----

		SettingsLogger settingsLogger = new SettingsLogger("./output/settings.log", startTime);
		settingsLogger.writeSettings(RunnerRtAmalWithActivities.class.getName(), seed, nTimeBins, timeBinSize_h,
				targetDeviationRunner.getRealPopulationSize(), targetDeviationRunner.getNRoundTrips(),
				targetDeviationRunner.getGauss(), convergenceCriterion, tolerance, msgInterval,
				iterCounter.getLastIteration(), targetDeviationRunner.getTarget2sigma(),
				targetDeviationRunner.getPrior().getClass().toString());
		settingsLogger.writeRuntimeInfo(iterCounter.getLastIteration());

		
		
		// ------------------ Run PythonPlotter ------------------

		new PythonRunner("src/main/python/estimation/plotting/01_nStops.py").run();
		new PythonRunner("src/main/python/estimation/plotting/01_nStops_histogram.py").run();
		new PythonRunner("src/main/python/estimation/plotting/02_od.py").run();
		new PythonRunner("src/main/python/estimation/plotting/03_location.py").run();
		new PythonRunner("src/main/python/estimation/plotting/04_dd.py").run();
		new PythonRunner("src/main/python/estimation/plotting/05_departures.py").run();
		new PythonRunner("src/main/python/estimation/plotting/06_logWeights.py").run();
		new PythonRunner("src/main/python/estimation/plotting/07_convergence.py").run();
		new PythonRunner("src/main/python/estimation/plotting/08_concatenate_pdfs.py").run();

	}
}
