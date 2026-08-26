package disaggregate.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import common.XMLStateLogger;
import disaggregate.model.Activity;
import disaggregate.model.Person;
import disaggregate.model.SurveyResponse;
import disaggregate.model.ActivityNode;
import disaggregate.rvu.MDDR;
import disaggregate.rvu.MDDRRepository;
import disaggregate.rvu.Trip;
import disaggregate.rvu.UPBD;
import disaggregate.rvu.UPBDRepository;
import disaggregate.weights.CompositeKernel;
import disaggregate.weights.Kernel;
import se.vti.roundtrips.common.Scenario;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.single.RoundTrip;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;

public class ScenarioUtils {
    static Logger logger = LogManager.getLogger(ScenarioUtils.class);
    public static List<Person> loadRespondents(UPBD upbd, MDDR mddr, int n) {
        List<Person> population = new ArrayList<>();

        for (String id : upbd.getIds()) {
            if (population.size() >= n) break;

            Trip trip = mddr.getTripById(id);
            Person p = new Person(population.size(),
                    upbd.getPersonHome(id),
                    upbd.getPersonAge(id),
                    upbd.getPersonIncome(id),
                    upbd.getPersonGender(id)
            );

            p.setName(id);
            p.assignPersonTrip(trip);
            try {
                p.validate();
                population.add(p);
            } catch (IllegalArgumentException e) {
                logger.warn("Skipping person {} due to validation error: {}", id, e.getMessage());
            }
        }

        return population;
    }

    public static <N extends ActivityNode> void writeNetwork(Scenario<N> scenario, Path filePath) {
        writeNetwork(scenario, filePath.toString());
    }

    public static boolean isDirectoryEmpty(Path path) throws IOException {
        try (var dirStream = Files.newDirectoryStream(path)) {
            return !dirStream.iterator().hasNext();
        }
    }

    public static void clearOutputDirectory(Path outputDir) throws IOException {
        if (!Files.exists(outputDir)) {
            return;
        }
        try (var paths = Files.walk(outputDir)) {
            paths.filter(path -> !path.equals(outputDir))
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to delete " + path, e);
                        }
                    });
        }
    }    

    public static <N extends ActivityNode> void writeNetwork(Scenario<N> scenario, String filePath) {
        try (XMLWriter writer = new XMLWriter(
                Files.newBufferedWriter(Path.of(filePath), StandardCharsets.UTF_8))) {

            writer.startDocument();
            writer.startElement("network");

            // --- nodes ---
            writer.startElement("nodes");
            for (N n : scenario.getNodesView()) {
                writer.startElement("node");
                for (Activity act : n.getAllActivities()) {
                    writer.attribute("activity", act.toString());
                }
                writer.attribute("id", n.toString());
                writer.endElement("node");
            }
            writer.endElement("nodes");

            // --- links (time + distance) ---
            writer.startElement("edges");
            for (N n1 : scenario.getNodesView()) {
                for (N n2 : scenario.getNodesView()) {
                    double tt = scenario.getTime_h(n1, n2);
                    double dist = scenario.getDistance_km(n1, n2);

                    if (Double.isNaN(tt) || Double.isInfinite(tt)) continue;

                    writer.startElement("edge");
                    writer.attribute("from", n1.toString());
                    writer.attribute("to", n2.toString());
                    writer.attribute("time_h", Double.toString(tt));
                    writer.attribute("distance_km", Double.toString(dist));
                    writer.endElement("edge");
                }
            }
            writer.endElement("edges");

            writer.endElement("network");

        } catch (IOException e) {
            throw new RuntimeException("Could not write network XML to " + filePath, e);
        }
    }

    public static <N extends ActivityNode> List<N> findHomes(Scenario<N> scenario, List<Person> population) {
//        public List<N> findHomes() {
        if (population.isEmpty() || scenario == null) return List.of();

        List<N> homes = new ArrayList<>();
        for (Person p : population) {
            boolean assigned = false;
            for (N node : scenario.getNodesView()) {
                if (node.getAllActivities().contains(Activity.HOME) && node.getBasicName().equals(p.getHomeZone())) {
                    homes.add(node);
                    assigned = true;
                    break;
                }
            }
            if (!assigned) {
                logger.warn("No home node for {} (zone {})", p.getName(), p.getHomeZone());
            }
        }

        if (homes.size() != population.size()) {
            throw new IllegalStateException("Mismatch in number of homes assigned. Have you run removePersonsWithoutValidHomes() or cleaned out the population data?");
        }
        return homes;
    }
    public static <N extends ActivityNode> List<Person> getPersonsWithoutValidHome(Scenario<N> scenario, List<Person> population) {
        List<Person> toRemove = new ArrayList<>();
        for (Person p : population) {
            boolean homeFound = false;
            for (N node : scenario.getNodesView()) {
                if (node.getBasicName().equals(p.getHomeZone())) {
                    if (!node.getAllActivities().contains(Activity.HOME)) {
                        continue;
                    }
                    homeFound = true;
                    break;
                }
            }
            if (!homeFound) {
                toRemove.add(p);
            }
        }
        return toRemove;
    }
    public static <N extends ActivityNode> MultiRoundTrip<N> createInitialMultiRoundTrip(Scenario<N> scenario, List<N> homes, List<Person> population) {
        MultiRoundTrip<N> result = new MultiRoundTrip<>(population.size());
        for (int ii = 0; ii < result.size(); ii++) {
            RoundTrip<N> rt = new RoundTrip<>(ii, List.of(homes.get(ii)), List.of(0));
            rt.setEpisodes(scenario.getOrCreateSimulator().simulate(rt));
            result.setRoundTripAndUpdateSummaries(ii, rt);
        }
        return result;
    }

    public static List<String> sampleWithoutReplacement(List<String> inputList, int n, Random r) {
//        Random r = new Random(seed);
        if (n > inputList.size()) {
            throw new IllegalArgumentException("Cannot sample more ids than available");
        }

        List<String> samplingList = new ArrayList<>(inputList);
        List<String> sampled = new ArrayList<>(n);

        for (int ii = 0; ii < n; ii++) {
            int idx = r.nextInt(samplingList.size());
            sampled.add(samplingList.get(idx));
            samplingList.remove(idx);
        }

        return sampled;
    }

    public static <N extends ActivityNode> boolean allActivitiesExists(Scenario<N> scenario, List<Person> population){
        Set<Activity> networkActivities = new HashSet<>();
        Set<Activity> surveyActivities = new HashSet<>();

        for (N node : scenario.getNodesView()) {
            networkActivities.addAll(node.getAllActivities());
        }

        for (Person person : population) {
            if (!person.hasTrip()) {
                throw new IllegalStateException("Person " + person.getName() + " does not have a trip assigned");
            }
            person.getTrip().getActivities().forEach(surveyActivities::add);
        }

        return networkActivities.containsAll(surveyActivities);
    }

    public static <N extends ActivityNode> void writeStateXML(java.nio.file.Path outputDir, MultiRoundTrip<N> mrt) {
        XMLStateLogger finalStateLogger = new XMLStateLogger(0, outputDir.resolve("finalState.xml"));
        finalStateLogger.start();
        finalStateLogger.processStateHook(mrt, 0.0);
        finalStateLogger.end();
    }    

    public static void writePopulation(java.util.List<Person> pop, java.nio.file.Path fp) {
        new common.PopulationWriter(pop, fp.toString()).write();
    }

    public static List<Person> loadPopulationFromFiles(Path datadir, String upbdFileName, String mddrFileName, Random random) throws Exception {
        UPBD upbd = new UPBDRepository()
        .fromCSV(datadir.resolve(upbdFileName).toString())
        .withRandom(random)
        .load();
        
        MDDR mddr = new MDDRRepository()
        .fromCSV(datadir.resolve(mddrFileName).toString())
        .withRandom(random)
        .load();
        
        List<Person> population = new ArrayList<>();
        int idx = 0;

        for (String personId : upbd.getIds()) {
            Person person = new Person(
                idx++,
                upbd.getPersonHome(personId),
                upbd.getPersonAge(personId),
                upbd.getPersonIncome(personId),
                upbd.getPersonGender(personId)
            );
            person.setName(personId);
            
            Trip trip = mddr.getTripById(personId);
            if (trip != null) {
                person.assignPersonTrip(trip);
            }
            
            population.add(person);
        }
        return population;
    }

    public static void loadNetworkFromTSV(Scenario<ActivityNode> scenario, Path networkDir) throws FileNotFoundException {
        Path nodesPath = networkDir.resolve("nodes.tsv");
        Path edgesPath = networkDir.resolve("edges.tsv");

        Map<String, ActivityNode> nodeMap = new HashMap<>();
        
        // Read nodes file - use row/col as x/y coordinates for ActivityNode (which extends ActivityNode)
        try (Scanner scanner = new Scanner(nodesPath.toFile())) {
            String header = scanner.nextLine();  // skip header: basicName name labels row col
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] parts = line.split("\t");
                String basicName = parts[0];  // e.g., "(0,0)"  (row, col) in GridWorld nodes. 
                String fullName = parts[1];   // e.g., "(0,0)[HOME]" 
                String labels = parts[2];     // e.g., "[HOME]"
                int row = Integer.parseInt(parts[3]);
                int col = Integer.parseInt(parts[4]);
                
                // Extract activity from labels
                String activityString = labels.replace("[", "").replace("]", "");
                Activity activity = Activity.valueOf(activityString);
                
                // ActivityNode extends ActivityNode - compatible with location kernels
                ActivityNode node = new ActivityNode(basicName, (double)col, (double)row, activity);  // name, x, y, activity
                nodeMap.put(fullName, node);
                scenario.addNode(node);
            }
        }

        try (Scanner scanner = new Scanner(edgesPath.toFile())) {
            String header = scanner.nextLine();  // skip header: from to tt dist
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] parts = line.split("\t");
                String fromName = parts[0];
                String toName = parts[1];
                double travelTime_h = Double.parseDouble(parts[2]);
                double distance_km = Double.parseDouble(parts[3]);
                
                ActivityNode fromNode = nodeMap.get(fromName);
                ActivityNode toNode = nodeMap.get(toName);
                
                if (fromNode != null && toNode != null) {
                    scenario.setTime_h(fromNode, toNode, travelTime_h);
                    scenario.setDistance_km(fromNode, toNode, distance_km);
                }
            }
        }        
    }

    public static void writeRunInfo(
            Path file,
            String name,
            long seed,
            int nIter,
            int msgInterval,
            Scenario<?> scenario,
            List<SurveyResponse<ActivityNode>> responses,
            Path dataDir,
            Path upbdPath,
            Path mddrPath,
            Path centroidsPath,
            Path zonePurposeAreasPath,
            Path sampersPopulationPath,
            Path outputDir,
            int agentCount
    ) throws IOException {
        int timeBinSize = scenario.getNumberOfTimeBins();
        int numTimeBins = scenario.getNumberOfTimeBins();
        double horizon = scenario.getPeriodLength_h();
        int nodeCount = scenario.getNodesView().size();
        int respondentCount = responses.size();
        int responseCount = responses.size();

        Kernel rootKernel = responses.get(0).getKernel();

        StringBuilder sb = new StringBuilder();

        sb.append("run:\n");
        sb.append("  timestamp_utc: ").append(q(Instant.now().toString())).append('\n');
        sb.append("  name: ").append(q(name)).append('\n');
        sb.append("  seed: ").append(seed).append('\n');
        sb.append("  iterations: ").append(nIter).append('\n');
        sb.append("  message_interval: ").append(msgInterval).append('\n');

        sb.append("time:\n");
        sb.append("  horizon_h: ").append(horizon).append('\n');
        sb.append("  time_bin_size_h: ").append(timeBinSize).append('\n');
        sb.append("  num_time_bins: ").append(numTimeBins).append('\n');

        sb.append("paths:\n");
        sb.append("  data_dir: ").append(q(dataDir.toString())).append('\n');
        sb.append("  upbd: ").append(q(upbdPath.toString())).append('\n');
        sb.append("  mddr: ").append(q(mddrPath.toString())).append('\n');
        sb.append("  centroids: ").append(q(centroidsPath.toString())).append('\n');
        sb.append("  zone_purpose_areas: ").append(q(zonePurposeAreasPath.toString())).append('\n');
        sb.append("  sampers_population: ").append(q(String.valueOf(sampersPopulationPath))).append('\n');
        sb.append("  output_dir: ").append(q(outputDir.toString())).append('\n');

        sb.append("kernels:\n");
        appendKernelTree(sb, rootKernel, "   ");

        sb.append("  schedule:\n");
        sb.append("    number_of_stops_weight: 0.3\n");
        sb.append("    total_travel_time_weight: 0.1\n");
        sb.append("    gaussian_schedule_overlap_weight: 0.2\n");
        sb.append("  location:\n");
        sb.append("    jaccard_activity_location_weight: 0.1\n");
        sb.append("    opportunity_weighted_location_weight: 0.4\n");
        sb.append("  population:\n");
        sb.append("    kernel_threshold: 1.0E-6\n");

        sb.append("counts:\n");
        sb.append("  respondents: ").append(respondentCount).append('\n');
        sb.append("  agents: ").append(agentCount).append('\n');
        sb.append("  responses: ").append(responseCount).append('\n');
        sb.append("  nodes: ").append(nodeCount).append('\n');

        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }


    private static String q(String s) {
        return "\"" + s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "\"";
    }

private static void appendKernelTree(StringBuilder sb, Kernel kernel, String pad) {
    sb.append(pad)
      .append("- class: ")
      .append(q(kernel.getClass().getSimpleName()))
      .append('\n');

    if (kernel instanceof CompositeKernel composite) {
        sb.append(pad).append("  components:\n");
        for (Kernel child : composite.getKernels()) {
            appendKernelTree(sb, child, pad + "    ");
        }
        return;
    }

    double[] params = kernel.getParameters();
    if (params != null && params.length > 0) {
        sb.append(pad).append("  parameters: [");
        for (int i = 0; i < params.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(params[i]);
        }
        sb.append("]\n");
    }
}

    public static Map<String, Map<Activity, Double>> toAttractivenessMap(
            Map<ActivityNode, Double> nodeWeights) {

        Map<String, Map<Activity, Double>> result = new HashMap<>();

        for (var e : nodeWeights.entrySet()) {
            ActivityNode node = e.getKey();
            double weight = e.getValue();

            result
                    .computeIfAbsent(node.getBasicName(), k -> new HashMap<>())
                    .merge(node.getActivity(), weight, Double::sum);
        }

        return result;
    }
}
