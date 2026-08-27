package synthesis;

import java.io.FileNotFoundException;
import java.io.PrintWriter;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import se.vti.roundtrips.common.Scenario;
import common.Activity;
import common.GridNodeWithActivity;
import se.vti.utils.misc.Tuple;

public class GridWorld{

	// --------------------- Fields --------------------
	Scenario<GridNodeWithActivity> scenario;

	private int gridSize;  // TODO: Should probably be final
	private double cityDiameter_km;
	private double travelSpeed_kmh;
	private double suburbFraction;

	private Tuple<Double, Double> center;

	private double edgeLength_km;
	private double edgeTime_h; 

	private Map<Activity, List<GridNodeWithActivity>> activityNodeMapping;
	private Map<GridNodeWithActivity, Integer> nodeOpportunities;
    private Map<Activity, Integer> totalOpportunities;

	// --------------------- Construction --------------------

	public GridWorld(Scenario<GridNodeWithActivity> scenario) {
		this.scenario = scenario;
		this.activityNodeMapping = new HashMap<>();
		this.nodeOpportunities = new HashMap<>();
        this.totalOpportunities = new HashMap<>();
	}

	// --------------------- Setter and getters --------------------

	public void setGridSize(int gridSize) {
		this.gridSize = gridSize;

		// Calculate the center of the grid
		this.center = new Tuple<>((gridSize - 1) / 2.0, (gridSize - 1) / 2.0);
	}

	public int getGridSize() {
		return this.gridSize;
	}

	public void setCityDiameter_km(int cityDiameter_km) {
		this.cityDiameter_km = cityDiameter_km;
	}

	public void setTravelSpeed_kmh(int travelSpeed_kmh) {
		this.travelSpeed_kmh = travelSpeed_kmh;
	}

	public void setSuburbFraction(double suburbFraction) {
		this.suburbFraction = suburbFraction;
	}

    public Map<GridNodeWithActivity, Integer> getNodeOpportunities() {
        return nodeOpportunities;
    }

	// --------------------- Helpers --------------------

	private double distanceToCenter(GridNodeWithActivity node) {
		double xDiff = node.row - this.center.getA();
		double yDiff = node.column - this.center.getB();
		return Math.sqrt(xDiff * xDiff + yDiff * yDiff);
	}

	public void calulateEdgeLengthAndEdgeTime() {
		this.edgeLength_km = this.cityDiameter_km / (this.gridSize - 1);
		this.edgeTime_h = this.edgeLength_km / this.travelSpeed_kmh;
	}

	public double computeEuclideanDistance(GridNodeWithActivity a, GridNodeWithActivity b) {
		int dx = a.row - b.row;
		int dy = a.column - b.column;
		return Math.sqrt(dx * dx + dy * dy);
	}

	// --------------------- Implementation --------------------

	public void calculateGrid(boolean euclideanDistance) {
		for (var originNode : this.scenario.getNodesView()) {
			for (var destinationNode : this.scenario.getNodesView()) {
				double edges;
				if (euclideanDistance) {
					edges = computeEuclideanDistance(originNode, destinationNode);
				} else {
					edges = originNode.computeGridDistance(destinationNode);
				}
				this.scenario.setDistance_km(originNode, destinationNode, edges * this.edgeLength_km);
				this.scenario.setTime_h(originNode, destinationNode, edges * this.edgeTime_h);
			}
		}
	}
	
	public void createNodesAndOpportunitiesForActivity(Activity act, double exponent, int totalOpp) {
		Random rnd = this.scenario.getRandom();
		createNodesAndOpportunitiesForActivity(act,exponent,totalOpp,rnd);
	}
	
	public void createNodesAndOpportunitiesForActivity(Activity act, double exponent, int totalOpp, Random rnd) {
		totalOpportunities.put(act, totalOpp);

		int G = gridSize * gridSize;

		// 1. Calculate power-law weights and their total sum
		double wiasum = 0.0;
		double[] weights = new double[G];
		for (int ii = 0; ii < G; ii++) {
			weights[ii] = Math.pow(ii+1, -exponent);
			wiasum += weights[ii];
		}

		double[] raw = new double[G];
		int[] counts = new int[G];
		int sum = 0;

		// 2. Normalize weights to scale up to totalOpp and floor the values
		for (int ii = 0; ii < G; ii++) {
			raw[ii] = totalOpp * weights[ii] / wiasum; // Normalized exact share
			counts[ii] = (int) Math.floor(raw[ii]);     // Initial integer count via flooring
			sum += counts[ii];
		}

		// 3. Calculate leftover units due to flooring
		int remaining = totalOpp - sum;

		// 4. Distribute leftover units to entries with the largest fractional remainders
		if (remaining > 0) {
			List<Integer> order = IntStream.range(0, G).boxed().collect(Collectors.toList());
			order.sort(Comparator.comparingDouble(ii -> -(raw[ii] - counts[ii])));

			for (int k = 0; k < remaining; k++) {
				counts[order.get(k)]++;
			}
		}

		// 5. Randomly shuffle the final counts across the grid cells
		List<Integer> shuffledCounts = Arrays.stream(counts).boxed().collect(Collectors.toList());
		Collections.shuffle(shuffledCounts, rnd);

		int idx = 0;
		for (int row = 0; row < gridSize; row++) {
			for (int col = 0; col < gridSize; col++) {
				int count = shuffledCounts.get(idx++);
				if (count > 0) {
					GridNodeWithActivity node = new GridNodeWithActivity(row, col, act);
					nodeOpportunities.put(node, count);
					activityNodeMapping.computeIfAbsent(act, k -> new ArrayList<>()).add(node);
					this.scenario.addNode(node);
				}
			}
		}
	}
	

	public List<GridNodeWithActivity> getHomeNodes() {
		if (this.activityNodeMapping.containsKey(Activity.HOME)) {
			return this.activityNodeMapping.get(Activity.HOME);
		} else {
			return new ArrayList<>();
		}
	}

	private Map<String, List<String>> computeGridMap() {
		Map<String, List<String>> gridMap = new HashMap<>();

		for (var node : this.scenario.getNodesView()) {
			String nodeStr = node.toString();
			String coords = nodeStr.substring(nodeStr.indexOf("(") + 1, nodeStr.indexOf(")"));
			String activity = nodeStr.substring(nodeStr.indexOf("[") + 1, nodeStr.indexOf("]"));
			gridMap.computeIfAbsent(coords, k -> new ArrayList<>()).add(activity);
		}
		return gridMap;
	}


	public void printGridWorld() {
		var gridMap = computeGridMap();

		for (int row = 0; row < this.gridSize; row++) {
			StringBuilder line = new StringBuilder();
			for (int col = 0; col < this.gridSize; col++) {
				String key = row + "," + col;
				if (gridMap.containsKey(key)) {
					List<String> activities = gridMap.get(key);
					List<String> activitiesAbbr = new ArrayList<>();
					for (String activity : activities) {
						if (activity.equals("HOME")) {
							activitiesAbbr.add("H");
						} else if (activity.equals("WORK")) {
							activitiesAbbr.add("W");
						} else if (activity.equals("EDUCATION")) {
							activitiesAbbr.add("E");
						} else if (activity.equals("OTHER")) {
							activitiesAbbr.add("O");
						} else {
							activitiesAbbr.add(activity);
						}
					}
					line.append("(").append(row).append(",").append(col)
					.append(",").append(String.join(",", activitiesAbbr)).append(") ");
				} else {
					line.append("(").append(row).append(",").append(col).append(",NULL) ");
				}
			}
			System.out.println(line);
		}
	}

	public void writeGridWorldToFile(String filePath) {
    try (PrintWriter writer = new PrintWriter(new java.io.File(filePath))) {

        // Map "row,col" -> int[activities]
        Map<String, int[]> cellOpps = new HashMap<>();

        for (var node : this.scenario.getNodesView()) {
            String key = node.row + "," + node.column;
            int[] counts = cellOpps.computeIfAbsent(key, k -> new int[Activity.values().length]);

            int opp = nodeOpportunities.getOrDefault(node, 0);
            counts[node.getActivity().ordinal()] += opp;
        }

        writer.println("row,col,HOME,WORK,EDUCATION,OTHER");

        // Write full grid, fill missing cells with zeros
        for (int row = 0; row < this.gridSize; row++) {
            for (int col = 0; col < this.gridSize; col++) {
                String key = row + "," + col;
                int[] counts = cellOpps.getOrDefault(key, new int[Activity.values().length]);

                int home = counts[Activity.HOME.ordinal()];
                int work = counts[Activity.WORK.ordinal()];
                int edu  = counts[Activity.EDUCATION.ordinal()];
                int other = counts[Activity.OTHER.ordinal()];

                writer.printf("%d,%d,%d,%d,%d,%d%n", row, col, home, work, edu, other);
            }
        }

    } catch (FileNotFoundException e) {
        e.printStackTrace();
    }
	}

private Map<String, Map<Activity, Integer>> computeCellActivityOpps() {
    Map<String, Map<Activity, Integer>> cellOpps = new HashMap<>();

    for (var node : this.scenario.getNodesView()) {
        String key = node.row + "," + node.column;

        Map<Activity, Integer> actMap =
            cellOpps.computeIfAbsent(key, k -> new HashMap<>());

        int opp = nodeOpportunities.getOrDefault(node, 0);
        actMap.merge(node.getActivity(), opp, Integer::sum);
    }
    return cellOpps;
}

public void writeGridWorldToJson(String filePath) {
    var cellOpps = computeCellActivityOpps();

    try (PrintWriter writer = new PrintWriter(new java.io.File(filePath))) {
        writer.println("{");
        writer.printf("  \"gridSize\": %d,%n", gridSize);
        writer.printf(Locale.US, "  \"cityDiameter_km\": %.2f,%n", cityDiameter_km);
        writer.printf(Locale.US, "  \"travelSpeed_kmh\": %.2f,%n", travelSpeed_kmh);
        writer.println("  \"cells\": {");

        // Optional: sort keys for stable output
        var keys = new ArrayList<>(cellOpps.keySet());
        keys.sort(String::compareTo);

        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            Map<Activity, Integer> actMap = cellOpps.get(key);

            writer.printf("    \"%s\": {", key);

            boolean firstAct = true;
            for (Activity act : actMap.keySet()) {
                Integer opp = actMap.get(act);
                // if (opp == null || opp == 0) continue;

                if (!firstAct) writer.print(", ");
                firstAct = false;
                writer.printf("\"%s\": %d", act.name(), opp);
            }

            writer.print("}");
            if (i < keys.size() - 1) {
                writer.println(",");
            } else {
                writer.println();
            }
        }

        writer.println("  }");
        writer.println("}");
    } catch (FileNotFoundException e) {
        e.printStackTrace();
    }
}
    public Set<GridNodeWithActivity> sampleActivityLocations(Activity act) {return sampleActivityLocations(act, 1, scenario.getRandom());}
    public Set<GridNodeWithActivity> sampleActivityLocations(Activity act, int n) {return sampleActivityLocations(act, n, scenario.getRandom());}
	public Set<GridNodeWithActivity> sampleActivityLocations(Activity act, Random rng) {return sampleActivityLocations(act, 1, rng);}
	public Set<GridNodeWithActivity> sampleActivityLocations(Activity act, int n, Random rng) {

        List<GridNodeWithActivity> activityNodes = new ArrayList<>(activityNodeMapping.get(act));
        Collections.sort(activityNodes, Comparator.comparingDouble(node -> nodeOpportunities.get(node)));

//        for (var node : activityNodes) {
//            System.out.println(node + " : " + nodeOpportunities.get(node));
//        }

        Set<GridNodeWithActivity> activitySet = new HashSet<>();

        int total = totalOpportunities.get(act);
        for (int ii = 0; ii < n; ii++) {
            double u = 1.0 - rng.nextDouble();
            int idx = 0;
            double cumulative = 0.0;
            GridNodeWithActivity node = null;
            while (idx < activityNodes.size() && u > cumulative) {
                node = activityNodes.get(idx++);
                cumulative += nodeOpportunities.get(node) / (double) total;
            }
            if (node == null) activityNodes.get(activityNodes.size() - 1);
            activityNodes.remove(node);
            activitySet.add(node);
            total -= nodeOpportunities.get(node);

            if (activityNodes.isEmpty()) break;
        }

        return activitySet;
    }

	public static GridWorld createUniform(Scenario<GridNodeWithActivity> scenario, boolean euclideanDistance, int gridSize) {
		GridWorld world = new GridWorld(scenario);
		world.setGridSize(gridSize);
		world.setCityDiameter_km(9);
		world.setTravelSpeed_kmh(30);
		world.calulateEdgeLengthAndEdgeTime();
		int n = gridSize * gridSize;

		Map<Activity, Integer> totalOpportunities = Map.of(
			Activity.HOME, n,
			Activity.WORK, n,
			Activity.EDUCATION, n,
			Activity.OTHER, n
		);

		for (Activity act : Activity.values()) {
			world.createNodesAndOpportunitiesForActivity(act, 0.0, totalOpportunities.get(act));
		}

		world.calculateGrid(euclideanDistance);
		return world;
	}
	
	public static GridWorld createDefault(Scenario<GridNodeWithActivity> scenario, int gridSize) {
		Random rnd = scenario.getRandom();
		return createDefault(scenario, gridSize, rnd);
	}

	public static GridWorld createDefault(Scenario<GridNodeWithActivity> scenario, int gridSize, Random rnd) {
		GridWorld world = new GridWorld(scenario);
		world.setGridSize(gridSize);
		world.setCityDiameter_km(10); // Gives 2km zone size for 5x5 Grid world
		world.setTravelSpeed_kmh(30);
		world.calulateEdgeLengthAndEdgeTime();

		// world.setSuburbFraction(0.125);
		// world.createSuburbs();
		// world.createCBD();
		// world.createSecondLayer();

		Map<Activity, Integer> totalOpportunities = Map.of(
			Activity.HOME, 1000,
			Activity.WORK, 1000,
			Activity.EDUCATION, 1000,
			Activity.OTHER, 1000
		);

		// Negative values place more opportunities near center. 
		// Values > 1 place more opportunities near edges
		Map<Activity, Double> exponents = Map.of(
			Activity.HOME, 0.0,
			Activity.WORK, 0.5,
			Activity.EDUCATION,1.4,
			Activity.OTHER, 1.0
		);

		for (Activity act : Activity.values()) {
			world.createNodesAndOpportunitiesForActivity(act, exponents.get(act), totalOpportunities.get(act), rnd);
		}

		world.calculateGrid(true);
		return world;
	}

	public static void main(String[] args) throws Exception {
		GridWorld world = GridWorld.createDefault(new Scenario<>(), 10);
		world.printGridWorld();
//         Runner.writeNetworkTsv(Path.of("."), world.scenario);

// 		world.writeGridWorldToJson("grid_world.json");
// //        String output = "C:/Users/MichaelS/Work Folders/Documents/work/RESEARCH/RENATE/writing/papers/roundtrips-paper/exploratoryimg";
//         String output = ".";
// 		try {
// 			new PythonRunner(
// 				"src/main/python/plotting/plotGridWorld.py",
// 				"--grid-world-file", "grid_world.json",
// 				"--save", "--show",
//                     "--save-dir", output + "/grid-world-30_100"
// 			).run();

//             new PythonRunner(
//                     "src/main/python/plotting/plot_network.py",
//                     "--results-dir", ".",
//                     "--save",
//                     "--save-dir", output + "/grid-world-30_100"
//             ).run();
// 		} catch (IOException | InterruptedException e) {
// 			e.printStackTrace();
// 		}
	}
}