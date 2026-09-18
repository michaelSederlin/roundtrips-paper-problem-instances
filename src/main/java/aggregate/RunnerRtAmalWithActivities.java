package aggregate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import aggregate.model.AtActivityLocationTargetWithActivities;
import aggregate.model.DDHomeSummaryWithActivities;
import aggregate.model.DDHomeTargetWithActivities;
import aggregate.model.DDWorkByGroupSummaryWithActivities;
import aggregate.model.DDWorkTargetWithActivities;
import aggregate.model.HomeLocationTargetWithActivities;
import aggregate.model.ODSummaryIgnoringActivities;
import aggregate.model.ODTargetIgnoringActivities;
import aggregate.utils.CsvReader;
import aggregate.utils.IterationCounter;
import aggregate.utils.LocationReader;
import aggregate.utils.PythonRunner;
import aggregate.utils.SettingsLogger;
import aggregate.utils.SimpleStatsLogger;
import aggregate.utils.TargetLoggerConcatenating;
import common.MatrixDataReader;
import common.logging.XMLStateLogger;

//import rt.model.location.AtActivityLocationTargetWithActivities;
//import rt.model.location.HomeLocationTargetWithActivities;
//import rt.model.departureDuration.DDHomeSummaryWithActivities;
//import rt.model.departureDuration.DDHomeTargetWithActivities;
//import rt.model.departureDuration.DDWorkByGroupSummaryWithActivities;
//import rt.model.departureDuration.DDWorkTargetWithActivities;
//import rt.model.od.ODSummaryIgnoringActivities;
//import rt.model.od.ODTargetIgnoringActivities;

//import rt.utils.logging.IterationCounter;
//import rt.utils.logging.SettingsLogger;
//import rt.utils.logging.SimpleStatsLogger;
//import rt.utils.logging.TargetLoggerConcatenating;

//import rt.utils.logging.XMLCompressor;
//import rt.utils.logging.XMLStateLogger;

//import rt.utils.pythonPlotting.PythonRunner;

//import rt.utils.reading.CsvReader;
//import rt.utils.reading.LocationReader;

//import rt.utils.reading.MatrixDataReader;

import se.vti.roundtrips.common.Node;
import se.vti.roundtrips.common.RandomRoundTripGenerator;
import se.vti.roundtrips.common.Runner;
import se.vti.roundtrips.common.ScenarioBuilder;
import se.vti.roundtrips.examples.activityExpandedGridNetwork.Activity;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.multiple.MultiRoundTripJsonIO;
import se.vti.roundtrips.multiple.grouping.PopulationGrouping;
import se.vti.roundtrips.samplingweights.SingleToMultiWeight;
import se.vti.roundtrips.samplingweights.StrictlyPeriodicSchedule;
import se.vti.roundtrips.samplingweights.priors.PopulationBinomialPrior;
import se.vti.utils.misc.Tuple;
import se.vti.utils.misc.metropolishastings.terminationcriteria.BlockAverageTerminationCriterion;


public class RunnerRtAmalWithActivities {

	public static void main(String[] args) throws IOException, InterruptedException {

		
		// ---- Fields -----
		
		LocalDateTime startTime = LocalDateTime.now();

		long seed = 4711l;
		String inputFolder = "./data/aggregate/measurement/";
		String outputFolder = "./data/aggregate/calibration_out/testing/";

		int nTimeBins = 24;
		double timeBinSize_h = 1;
		int realPopulationSize = 1000;
		int nRoundTrips = 1000;

		boolean gauss = true;
		boolean discretizationNoise = true;
		double sigma = 2;

		boolean convergenceCriterion = true;
		double tolerance = 0.2;
		long nIterations = 1 * 1000 * 1000;
		long msgInterval = 5000;



		// ----- Testing ----- 
		
		convergenceCriterion = false;
		nIterations = 1 * 10 * 1000;
		msgInterval = nIterations/200; 



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
		var runner = new Runner<Node>(scenario);
		LinkedHashMap<String, Double> target2sigma = new LinkedHashMap<>();
		
		var popGroup2zone2target  = readGroup2Zone2Target(inputFolder + "25_homeLocations.csv");
		var workGroup2zone2target = readGroup2Zone2Target(inputFolder + "25_workLocations.csv");

		PopulationGrouping populationGroupingHome = createGrouping(popGroup2zone2target,  nRoundTrips);
		PopulationGrouping populationGroupingWork = createGrouping(workGroup2zone2target, nRoundTrips);
		


		// ---- Prior -----

		StrictlyPeriodicSchedule<Node> strictlyPeriodicSchedule = new StrictlyPeriodicSchedule<>(scenario.getPeriodLength_h());
		runner.addPopulationWeight(new SingleToMultiWeight<>(strictlyPeriodicSchedule));
		runner.setPopulationPrior(new PopulationBinomialPrior<>(scenario, 3.125)); // TODO double check



		// ---- OD -----
		
		target2sigma.put("OD", sigma);
		var odTarget = new ODTargetIgnoringActivities<Node>(realPopulationSize);
		var data = MatrixDataReader.readWithSemicolonSeparator(inputFolder + "25_od.csv").entrySet();
		for (Map.Entry<Tuple<String, String>, Double> entry : data) {

			var od = entry.getKey();
			odTarget.setODEntry(od.getA(), od.getB(), entry.getValue());
			if (gauss) {
				odTarget.setToGaussian(sigma);
			} else {
				odTarget.setToTwoSidedExponential(sigma);
			}
			odTarget.setAccountForDiscretizationNoise(discretizationNoise);
		}
		runner.addStateProcessor(new TargetLoggerConcatenating<>(msgInterval, odTarget, outputFolder + "odTarget.csv"));
		runner.addPopulationWeight(odTarget);



		// ---- HL target  -----
		
		target2sigma.put("HL", sigma);
		for (Map.Entry<String, Map<String, Double>> entryInGroup2zone2target : popGroup2zone2target.entrySet()) {
			HomeLocationTargetWithActivities<Node> homeLocationTarget = new HomeLocationTargetWithActivities<>(
					realPopulationSize);
			if (gauss) {
				homeLocationTarget.setToGaussian(sigma);
			} else {
				homeLocationTarget.setToTwoSidedExponential(sigma);
			}
			homeLocationTarget.setAccountForDiscretizationNoise(discretizationNoise);
			for (Map.Entry<String, Double> zone2target : entryInGroup2zone2target.getValue().entrySet()) {
				homeLocationTarget.setTarget(zone2target.getKey(), zone2target.getValue());
			}
			homeLocationTarget.setFilter(populationGroupingHome.createFilter(entryInGroup2zone2target.getKey()));

			runner.addStateProcessor(new TargetLoggerConcatenating<>(msgInterval, homeLocationTarget,
					"homeTarget_" + entryInGroup2zone2target.getKey() + ".csv", outputFolder));
			runner.addPopulationWeight(homeLocationTarget);
		}



		
		// ---- WL target -----
		
		target2sigma.put("WL", sigma);
		for (Map.Entry<String, Map<String, Double>> entryInWorkGroup2zone2target : workGroup2zone2target.entrySet()) {
			AtActivityLocationTargetWithActivities<Node> workLocationTarget = new AtActivityLocationTargetWithActivities<>(
					Activity.WORK, realPopulationSize, 0, 24.0, 0);			if (gauss) {
				workLocationTarget.setToGaussian(sigma);
			} else {
				workLocationTarget.setToTwoSidedExponential(sigma);
			}
			workLocationTarget.setAccountForDiscretizationNoise(discretizationNoise);
			for (Map.Entry<String, Double> zone2target : entryInWorkGroup2zone2target.getValue().entrySet()) {
				workLocationTarget.setTarget(zone2target.getKey(), zone2target.getValue());
			}
			workLocationTarget.setFilter(populationGroupingWork.createFilter(entryInWorkGroup2zone2target.getKey()));

			runner.addStateProcessor(new TargetLoggerConcatenating<>(msgInterval, workLocationTarget,
					"workTarget_" + entryInWorkGroup2zone2target.getKey() + ".csv", outputFolder));
			runner.addPopulationWeight(workLocationTarget);
		}

		

		// ---- DD home target  -----
		
		target2sigma.put("DDH", sigma);
		DDHomeTargetWithActivities ddHomeTarget = new DDHomeTargetWithActivities(realPopulationSize);
		double[][] inputArrayHome = CsvReader.csvTo2dArray(inputFolder + "25_ddHome.csv", ";");
		for (int row = 0; row < inputArrayHome.length; row++) {
			for (int col = 0; col < inputArrayHome[row].length; col++) {
				ddHomeTarget.setDDEntry(row, col, inputArrayHome[row][col]);
				if (gauss) {
					ddHomeTarget.setToGaussian(sigma);
				} else {
					ddHomeTarget.setToTwoSidedExponential(sigma);
				}
				ddHomeTarget.setAccountForDiscretizationNoise(discretizationNoise);
			}
		}
		runner.addStateProcessor(new TargetLoggerConcatenating<>(msgInterval, ddHomeTarget, "ddHomeTarget.csv", outputFolder));
		runner.addPopulationWeight(ddHomeTarget);


		// ---- DD work target  -----
		
		target2sigma.put("DDW", sigma);
		DDWorkTargetWithActivities ddWorkTarget = new DDWorkTargetWithActivities(realPopulationSize,workGroup2zone2target.keySet());
		double[][] inputArrayWork = CsvReader.csvTo2dArray(inputFolder + "25_ddWork.csv", ";");
		for (int row = 0; row < inputArrayWork.length; row++) {
			for (int col = 0; col < inputArrayWork[row].length; col++) {
				ddWorkTarget.setDDEntry(row, col, inputArrayWork[row][col]);
				if (gauss) {
					ddWorkTarget.setToGaussian(sigma);
				} else {
					ddWorkTarget.setToTwoSidedExponential(sigma);
				}
				ddWorkTarget.setAccountForDiscretizationNoise(discretizationNoise);
			}
		}
		runner.addStateProcessor(new TargetLoggerConcatenating<>(msgInterval, ddWorkTarget, "ddWorkTarget.csv", outputFolder));
		runner.addPopulationWeight(ddWorkTarget);




		// ------ InitState ----

		MultiRoundTrip<Node> initialState = new MultiRoundTrip<>(nRoundTrips);
		initialState.addSummary(new ODSummaryIgnoringActivities<>());
		initialState.addSummary(new DDHomeSummaryWithActivities());
		initialState.addSummary(new DDWorkByGroupSummaryWithActivities(populationGroupingWork, workGroup2zone2target.keySet()));
		var generator = new RandomRoundTripGenerator<Node>(scenario)
				.setNumberOfStayEpisodesInterval(2, 2)
				.setFeasibilityCheck(r -> (!Double.isInfinite(strictlyPeriodicSchedule.logWeight(r))));
		generator.populateRandomly(initialState);
		runner.setInitialState(initialState);



		// ----- StateProcessors -----

		runner.configureWeightLogging(outputFolder + "/logWeights.log", msgInterval);
		runner.addStateProcessor(new SimpleStatsLogger<>(scenario, msgInterval, outputFolder));
		runner.addStateProcessor(new XMLStateLogger<>(msgInterval, outputFolder + "/states.xml"));	
		var iterCounter = new IterationCounter<MultiRoundTrip<Node>>();
		runner.addStateProcessor(iterCounter);



		// ------ Run MH -------

		if (convergenceCriterion) {
			var terminationCriterion = new BlockAverageTerminationCriterion<MultiRoundTrip<Node>>()
					.setCheckInterval((int) msgInterval).setMinSamples((int) msgInterval)
					.setConvergenceStatsFileName(outputFolder + "/convergenceStats.tsv")
					.setRelativeVarianceTolerance(tolerance).setStandardizedMeanTolerance(tolerance);
			runner.setTerminationCriterion(terminationCriterion);
		} else {
			runner.setNumberOfIterations(nIterations);
		}
		runner.run();



		// ----- Log final state -----

		var finalState = runner.getFinalState();
		MultiRoundTripJsonIO.singleton().writeToFile(finalState, outputFolder + "/final_state.json");



		// ----- Log Settings -----

		SettingsLogger settingsLogger = new SettingsLogger(outputFolder + "/settings.log", startTime);
		settingsLogger.writeSettings(
				RunnerRtAmalWithActivities.class.getName(), 
				seed, 
				nTimeBins, 
				timeBinSize_h,
				realPopulationSize, 
				nRoundTrips,
				gauss, 
				convergenceCriterion, 
				tolerance, 
				msgInterval,
				iterCounter.getLastIteration(), 
				target2sigma,
				"TODO log prior");
		settingsLogger.writeRuntimeInfo(iterCounter.getLastIteration());



		// ------------------ Run PythonPlotter ------------------

//		new PythonRunner(
//				"src/main/python/roundtrips/plotting/new/plot_results_aggregated.py",
//				"--calibration", "./output/",
//				"--measurement", "./input/amal/measurementData/",
//				"--validation",  "./input/amal/validationData/",
//				"--out",         "./output/"
//				,"--join"
//				).run();
	}
	
	
	
	// ------------------ Helper  ------------------

	private static Map<String, Map<String, Double>> readGroup2Zone2Target(String file) {

	    Map<String, Map<String, Double>> group2zone2target = new LinkedHashMap<>();
	    for (Map.Entry<Tuple<String, String>, Double> entry :
	            MatrixDataReader.readWithSemicolonSeparator(file).entrySet()) {
	        String group = entry.getKey().getA();
	        String zone = entry.getKey().getB();
	        group2zone2target
	                .computeIfAbsent(group, g -> new LinkedHashMap<>())
	                .put(zone, entry.getValue());
	    }
	    return group2zone2target;
	}


	private static PopulationGrouping createGrouping(Map<String, Map<String, Double>> group2zone2target,
	                                                 int nRoundTrips) {

	    PopulationGrouping grouping = new PopulationGrouping(nRoundTrips);
	    group2zone2target.forEach((group, zone2target) ->
	            grouping.addGroup(group, zone2target.values().stream().mapToDouble(c -> c).sum()));
	    group2zone2target.keySet().forEach(grouping::createFilter);
	    return grouping;
	}
}
