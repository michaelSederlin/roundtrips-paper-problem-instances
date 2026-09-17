package aggregate;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import aggregate.model.AtActivityLocationTargetWithActivities;
import aggregate.model.DDHomeTargetWithActivities;
import aggregate.model.DDWorkTargetWithActivities;
import aggregate.model.HomeLocationTargetWithActivities;
import aggregate.model.ODTargetIgnoringActivities;
import aggregate.model.nStopsTarget;
import aggregate.utils.CsvReader;
import aggregate.utils.MatrixDataReader;
import aggregate.utils.TargetLogger;
import aggregate.utils.TargetLoggerConcatenating;

import se.vti.roundtrips.common.Node;
import se.vti.roundtrips.common.Runner;
import se.vti.roundtrips.common.Scenario;
import se.vti.roundtrips.examples.activityExpandedGridNetwork.Activity;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.multiple.grouping.PopulationGrouping;
import se.vti.utils.misc.Tuple;
import se.vti.utils.misc.metropolishastings.MHWeight;

/**
 * Activity-specific modification: Uses HomeLocationTargetWithActivities,
 * AtActivityLocationTargetWithActivities(Activity.WORK, ...), removed
 * setWlTargetAmal, removed setDdWorkTargetAmal
 * 
 * 
 * @author frupprecht
 */

public class TargetDeviationRunnerWithActivities extends Runner<Node> {

	// -------- Fields ----------

	private int realPopulationSize;
	private Scenario<Node> scenario;
	private LinkedHashMap<String, Double> target2sigma = new LinkedHashMap<>();
	private boolean gauss;
	private boolean discretizationNoise;

	private PopulationGrouping populationGrouping;
	private Map<String, Map<String, Double>> popGroup2zone2target;
	private Map<String, Map<String, Double>> workGroup2zone2target;

	private int nRoundTrips;
	private long msgInterval;

	// -------- Constructor ----------

	public TargetDeviationRunnerWithActivities(Scenario<Node> scenario) {
		super(scenario);
		this.scenario = scenario;
	}

	// -------- Setter & Getter -----------

	public void setRealPopulationSize(int realPopulationSize) {
		this.realPopulationSize = realPopulationSize;
	}

	public int getRealPopulationSize() {
		return this.realPopulationSize;
	}

	public void setNRoundTrips(int nRoundTrips) {
		this.nRoundTrips = nRoundTrips;
	}

	public int getNRoundTrips() {
		return this.nRoundTrips;
	}

	public PopulationGrouping getPopulationGrouping() {
		return this.populationGrouping;
	}

	public Map<String, Map<String, Double>> getWorkGroup2zone2target() {
		return this.workGroup2zone2target;
	}

	public LinkedHashMap<String, Double> getTarget2sigma() {
		return this.target2sigma;
	}

	public boolean getGauss() {
		return gauss;
	}

	public void setGauss(boolean gauss) {
		this.gauss = gauss;
	}

	public void setDiscretizationNoise(boolean discretizationNoise) {
		this.discretizationNoise = discretizationNoise;
	}

	public void setMsgInterval(long msgIntervall) {
		this.msgInterval = msgIntervall;
		super.setMessageInterval(msgIntervall);
	}

	public MHWeight<Node> getPrior() {
		try {
			Field priorField = Runner.class.getDeclaredField("prior");
			priorField.setAccessible(true);
			return (MHWeight<Node>) priorField.get(this);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			throw new RuntimeException("Fehler beim Zugriff auf das 'prior'-Feld via Reflection", e);
		}
	}

	// -------- Add StriclyWeight ---------

	public void addWeight(MHWeight<MultiRoundTrip<Node>> weight) {
		super.addPopulationWeight(weight);
		target2sigma.put(weight.getClass().getSimpleName().toString(), 0.0);
	}

	// -------- Set Target Weights ----------

	public void setNstopsTarget(double sigma, String inputDataFile, String outputFolder) {
		target2sigma.put("nStops", sigma);
		nStopsTarget<Node> nStopsTarget = new nStopsTarget<>(realPopulationSize, scenario.getTimeBinCnt());
		nStopsTarget.setTarget(CsvReader.csvTo1dArray(inputDataFile, ";"));
		if (this.gauss) {
			nStopsTarget.setToGaussian(sigma);
		} else {
			nStopsTarget.setToTwoSidedExponential(sigma);
		}
		nStopsTarget.setAccountForDiscretizationNoise(discretizationNoise);
		this.addStateProcessor(new TargetLogger<>(msgInterval, nStopsTarget, "nStopsTarget.csv", outputFolder));
		this.addPopulationWeight(nStopsTarget);
	}

	public void setOdTarget(double sigma, String inputDataFile, String outputFolder) {
		target2sigma.put("OD", sigma);
		var odTarget = new ODTargetIgnoringActivities<Node>(this.realPopulationSize);
		var data = MatrixDataReader.readWithSemicolonSeparator(inputDataFile).entrySet();
		for (Map.Entry<Tuple<String, String>, Double> entry : data) {
//			Node originNode = RoundTripsUtils.getOrCreateLocationWithSameName(this.scenario,
//					new Node(entry.getKey().getA())); 
//			Node destNode = RoundTripsUtils.getOrCreateLocationWithSameName(this.scenario,
//					new Node(entry.getKey().getB()));
//			((ODTargetIgnoringActivities<Node>) odTarget).setODEntry(od.getA(), od.getB(), entry.getValue());
			var od = entry.getKey();
			odTarget.setODEntry(od.getA(), od.getB(), entry.getValue());
			if (this.gauss) {
				odTarget.setToGaussian(sigma);
			} else {
				odTarget.setToTwoSidedExponential(sigma);
			}
			odTarget.setAccountForDiscretizationNoise(discretizationNoise);
		}
		this.addStateProcessor(new TargetLoggerConcatenating<>(msgInterval, odTarget, outputFolder + "odTarget.csv"));
		this.addPopulationWeight(odTarget);
	}

	public void setHlTarget(double sigma, String outputFolder) {
		target2sigma.put("HL", sigma);
		for (Map.Entry<String, Map<String, Double>> entryInGroup2zone2target : this.popGroup2zone2target.entrySet()) {
			HomeLocationTargetWithActivities<Node> homeLocationTarget = new HomeLocationTargetWithActivities<>(
					realPopulationSize);
			if (this.gauss) {
				homeLocationTarget.setToGaussian(sigma);
			} else {
				homeLocationTarget.setToTwoSidedExponential(sigma);
			}
			homeLocationTarget.setAccountForDiscretizationNoise(discretizationNoise);
			for (Map.Entry<String, Double> zone2target : entryInGroup2zone2target.getValue().entrySet()) {
				homeLocationTarget.setTarget(zone2target.getKey(), zone2target.getValue());
			}
			homeLocationTarget.setFilter(this.populationGrouping.createFilter(entryInGroup2zone2target.getKey()));

			this.addStateProcessor(new TargetLoggerConcatenating<>(msgInterval, homeLocationTarget,
					"homeTarget_" + entryInGroup2zone2target.getKey() + ".csv", outputFolder));
			this.addPopulationWeight(homeLocationTarget);
		}
	}

	public void setWlTarget(double sigma, String outputFolder) {
		target2sigma.put("WL", sigma);
		for (Map.Entry<String, Map<String, Double>> entryInWorkGroup2zone2target : workGroup2zone2target.entrySet()) {
			AtActivityLocationTargetWithActivities<Node> workLocationTarget = new AtActivityLocationTargetWithActivities<>(
					Activity.WORK, realPopulationSize, 0, 24.0, 0);
			if (this.gauss) {
				workLocationTarget.setToGaussian(sigma);
			} else {
				workLocationTarget.setToTwoSidedExponential(sigma);
			}
			workLocationTarget.setAccountForDiscretizationNoise(discretizationNoise);
			for (Map.Entry<String, Double> zone2target : entryInWorkGroup2zone2target.getValue().entrySet()) {
				workLocationTarget.setTarget(zone2target.getKey(), zone2target.getValue());
			}
			workLocationTarget.setFilter(this.populationGrouping.createFilter(entryInWorkGroup2zone2target.getKey()));

			this.addStateProcessor(new TargetLoggerConcatenating<>(msgInterval, workLocationTarget,
					"workTarget_" + entryInWorkGroup2zone2target.getKey() + ".csv", outputFolder));
			this.addPopulationWeight(workLocationTarget);
		}
	}

//	public void setWlTargetAmal(double sigma, Set<String> groupsSecondLongest, String outputFolder) {
//	    target2sigma.put("WL", sigma);
//
//	    for (Map.Entry<String, Map<Node, Double>> entryInWorkGroup2zone2target : workGroup2zone2target.entrySet()) {
//	        String group = entryInWorkGroup2zone2target.getKey();
//
//	        // Select target implementation based on group:
//	        // - group18: second-longest overlap
//	        // - rest: longest overlap
//	        TargetDeviationWeight<Node> workLocationTarget;
//	        if (groupsSecondLongest.contains(group)) {
//	            workLocationTarget = new AtMainActivityLocationTargetSecondLongest<>(realPopulationSize, 0, 24.0, 0);
//	        } else {
//	            workLocationTarget = new AtMainActivityLocationTarget<>(realPopulationSize, 0, 24.0, 0);
//	        }
//
//	        if (this.gauss) {
//	            workLocationTarget.setToGaussian(sigma);
//	        } else {
//	            workLocationTarget.setToTwoSidedExponential(sigma);
//	        }
//	        workLocationTarget.setAccountForDiscretizationNoise(discretizationNoise);
//
//	        for (Map.Entry<Node, Double> zone2target : entryInWorkGroup2zone2target.getValue().entrySet()) {
//	            if (workLocationTarget instanceof AtMainActivityLocationTarget<Node> t1) {
//	                t1.setTarget(zone2target.getKey(), zone2target.getValue());
//	            } else if (workLocationTarget instanceof AtMainActivityLocationTargetSecondLongest<Node> t2) {
//	                t2.setTarget(zone2target.getKey(), zone2target.getValue());
//	            }
//	        }
//	        workLocationTarget.setFilter(this.populationGrouping.createFilter(group));
//
//	        this.addStateProcessor(new TargetLoggerConcatenating<>(msgInterval, workLocationTarget,
//	                "workTarget_" + group + ".csv", outputFolder));
//	        this.addPopulationWeight(workLocationTarget);
//	    }
//	}

	public void setDdHomeTarget(double sigma, String inputFile, String outputFolder) {
		target2sigma.put("DDHome", sigma);
		DDHomeTargetWithActivities ddHomeTarget = new DDHomeTargetWithActivities(realPopulationSize);
		double[][] inputArrayHome = CsvReader.csvTo2dArray(inputFile, ";");
		for (int row = 0; row < inputArrayHome.length; row++) {
			for (int col = 0; col < inputArrayHome[row].length; col++) {
				ddHomeTarget.setDDEntry(row, col, inputArrayHome[row][col]);
				if (this.gauss) {
					ddHomeTarget.setToGaussian(sigma);
				} else {
					ddHomeTarget.setToTwoSidedExponential(sigma);
				}
				ddHomeTarget.setAccountForDiscretizationNoise(discretizationNoise);
			}
		}
		this.addStateProcessor(
				new TargetLoggerConcatenating<>(msgInterval, ddHomeTarget, "ddHomeTarget.csv", outputFolder));
		this.addPopulationWeight(ddHomeTarget);
	}

	public void setDdWorkTarget(double sigma, String inputFile, String outputFolder) {
		target2sigma.put("DDWork", sigma);
		DDWorkTargetWithActivities ddWorkTarget = new DDWorkTargetWithActivities(realPopulationSize,
				this.workGroup2zone2target.keySet());
		double[][] inputArrayWork = CsvReader.csvTo2dArray(inputFile, ";");
		for (int row = 0; row < inputArrayWork.length; row++) {
			for (int col = 0; col < inputArrayWork[row].length; col++) {
				ddWorkTarget.setDDEntry(row, col, inputArrayWork[row][col]);
				if (this.gauss) {
					ddWorkTarget.setToGaussian(sigma);
				} else {
					ddWorkTarget.setToTwoSidedExponential(sigma);
				}
				ddWorkTarget.setAccountForDiscretizationNoise(discretizationNoise);
			}
		}
		this.addStateProcessor(
				new TargetLoggerConcatenating<>(msgInterval, ddWorkTarget, "ddWorkTarget.csv", outputFolder));
		this.addPopulationWeight(ddWorkTarget);
	}

//	public void setDdWorkTargetAmal(double sigma, Set<String> groupsSecondLongest, String inputFile,
//			String outputFolder) {
//		target2sigma.put("DDWork", sigma);
//		var ddWorkTargetAmal = new DDWorkTargetAmal(realPopulationSize, this.getWorkGroup2zone2target().keySet(),
//				groupsSecondLongest);
//		double[][] inputArrayWork = CsvReader.csvTo2dArray(inputFile, ";");
//		for (int row = 0; row < inputArrayWork.length; row++) {
//			for (int col = 0; col < inputArrayWork[row].length; col++) {
//				ddWorkTargetAmal.setDDEntry(row, col, inputArrayWork[row][col]);
//			}
//		}
//		if (gauss) {
//			ddWorkTargetAmal.setToGaussian(sigma);
//		} else {
//			ddWorkTargetAmal.setToTwoSidedExponential(sigma);
//		}
//		ddWorkTargetAmal.setAccountForDiscretizationNoise(discretizationNoise);
//		this.addStateProcessor(
//				new TargetLoggerConcatenating<>(msgInterval, ddWorkTargetAmal, "ddWorkTarget.csv", outputFolder));
//		this.addPopulationWeight(ddWorkTargetAmal);
//	}

	// --------------- Create Population Grouping --------------

	public void createPopulatinGrouping(String inputFolder) {
		this.readPopGroup2Zone2Target(inputFolder);
		this.readWorkGroup2zone2target(inputFolder);
		this.populationGrouping = new PopulationGrouping(nRoundTrips);
		for (Map.Entry<String, Map<String, Double>> entry : popGroup2zone2target.entrySet()) {
			double populationGroupSize = entry.getValue().values().stream().mapToDouble(c -> c).sum();
			populationGrouping.addGroup(entry.getKey(), populationGroupSize);
		}
	}

	public void readPopGroup2Zone2Target(String inputFolder) {
		this.popGroup2zone2target = new LinkedHashMap<>();
		for (Map.Entry<Tuple<String, String>, Double> entry : MatrixDataReader
				.readWithSemicolonSeparator(inputFolder + "homeLocations.csv").entrySet()) {
			String populationGroup = entry.getKey().getA();
//			Node zone = scenario.getNode(entry.getKey().getB());
			String zone = entry.getKey().getB();

			double target = entry.getValue();
			Map<String, Double> zone2target = this.popGroup2zone2target.computeIfAbsent(populationGroup,
					g -> new LinkedHashMap<>());
			zone2target.put(zone, target);
		}
	}

	public void readWorkGroup2zone2target(String inputFolder) {
		this.workGroup2zone2target = new LinkedHashMap<>();
		for (Map.Entry<Tuple<String, String>, Double> entry : MatrixDataReader
				.readWithSemicolonSeparator(inputFolder + "workLocations.csv").entrySet()) {
			String group = entry.getKey().getA();
			Map<String, Double> zone2target = this.workGroup2zone2target.computeIfAbsent(group,
					g -> new LinkedHashMap<>());
//			Node zone = scenario.getNode(entry.getKey().getB());
			String zone = entry.getKey().getB();
			double target = entry.getValue();
			zone2target.put(zone, target);
		}
	}
}