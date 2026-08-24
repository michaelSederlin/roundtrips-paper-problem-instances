package disaggregate.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import disaggregate.model.Activity;
import disaggregate.model.nodes.NodeWithActivity;
import se.vti.roundtrips.common.Scenario;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LoadOpportunityWeights {
    private static final double DEFAULT_WEIGHT = 0.0;
    private static final Logger logger = LogManager.getLogger(LoadOpportunityWeights.class);

    private static String[] parseCSVLine(String line) {
        List<String> fields = new ArrayList<>();
        Matcher matcher = Pattern.compile("\"([^\"]*)\"|([^,]+)").matcher(line);

        while (matcher.find()) {
            fields.add(matcher.group(1) != null ? matcher.group(1) : matcher.group(2).trim());
        }

        return fields.toArray(new String[0]);
    }
    public static Map<NodeWithActivity, Double> load(Path fp, Scenario<NodeWithActivity> scenario) {return load(fp.toString(), scenario);}
    public static Map<NodeWithActivity, Double> load(String fp, Scenario<NodeWithActivity> scenario) {
        // Assumes an activity expanded network in the outputs.

        Map<String, Map<Activity, Double>> zoneActivityWeights = load(fp);
        Map<NodeWithActivity, Double> nodeActivityWeights = new HashMap<>();

        Map<Activity, Integer> skipped = new EnumMap<>(Activity.class);
        Map<Activity, Integer> activityNodeCounts = new EnumMap<>(Activity.class);

        for (NodeWithActivity node : scenario.getNodesView()) {
            String nodeName = node.getBasicName();
            activityNodeCounts.merge(node.getActivity(), 1, Integer::sum);

            Map<Activity, Double> activityWeights = zoneActivityWeights.get(nodeName);

            if (activityWeights == null) {
                throw new RuntimeException("No opportunity weights found for node: " + nodeName);
            }

            Double weight = activityWeights.get(node.getActivity());
            if (weight == null) {
                logger.warn("No opportunity weight found for node: {} with activity: {}. Using default value {}.",
                        nodeName, node.getActivity(), DEFAULT_WEIGHT);
                weight = DEFAULT_WEIGHT;

                skipped.merge(node.getActivity(), 1, Integer::sum);
            }

            nodeActivityWeights.put(node, weight);
        }

        logger.info("Loaded opportunity weights for {} nodes.", nodeActivityWeights.size());
        logger.info("Skipped activities: {}",
                activityNodeCounts.entrySet().stream()
                        .map(e -> {
                            Activity activity = e.getKey();
                            int total = e.getValue();
                            int skippedCount = skipped.getOrDefault(activity, 0);
                            double ratio = total == 0 ? 0.0 : (double) skippedCount / total;

                            return String.format("%s=%d/%d (%.2f%%)",
                                    activity, skippedCount, total, ratio * 100.0);
                        })
                        .collect(Collectors.joining(", "))
        );
        return nodeActivityWeights;
    }

    public static Map<String, Map<Activity, Double>> load(String fp) {
        logger.info("Loading opportunity weights from {}", fp);
        List<String[]> rows = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(fp.endsWith(".gz")
                ? new java.io.InputStreamReader(new java.util.zip.GZIPInputStream(new java.io.FileInputStream(fp)))
                : new FileReader(fp))) {
            String line;
            while ((line = reader.readLine()) != null) {
                rows.add(parseCSVLine(line));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read CSV file: " + fp, e);
        }

        String[] header = rows.getFirst();
        rows.removeFirst();

        int zoneIdx = -1;
        int purposeIdx = -1;
        int areaIdx = -1;

        for (int ii = 0; ii < header.length; ii++) {
            if ("samscode".equals(header[ii])) zoneIdx = ii;
            if ("ANDAMAL".equals(header[ii])) purposeIdx = ii;
            if ("area".equals(header[ii])) areaIdx = ii;
        }

        if (zoneIdx == -1 || purposeIdx == -1 || areaIdx == -1) {
            throw new RuntimeException("didnt find one of the columns (samscode: %d, ANDAMAL: %d, area: %d)"
                    .formatted(zoneIdx, purposeIdx, areaIdx));
        }

        Map<String, Map<Activity, Double>> opportunityWeights = new HashMap<>();
        for (String[] row : rows) {
            String zone = row[zoneIdx];
            Activity act = Activity.fromString(row[purposeIdx]);
            double area = Double.parseDouble(row[areaIdx]);

            opportunityWeights.computeIfAbsent(zone, k -> new HashMap<>()).put(act, area);
        }

        logger.info("Loaded opportunity weights for {} zones.", opportunityWeights.size());
        return opportunityWeights;
    }

    public static Map<NodeWithActivity, Double> normalizeByMeanSigma(Map<NodeWithActivity, Double> opportunityWeights) {
        Map<Activity, Double> meanSigma = new EnumMap(Activity.class);
        for (Activity act : Activity.values()) {
            Double sumSigma = 0.0;
            int counter = 0;
            for (Map.Entry<NodeWithActivity, Double> entry : opportunityWeights.entrySet()){
                if (entry.getKey().getActivity().equals(act)) {
                    sumSigma += entry.getValue();
                    counter ++;
                }
            }
            meanSigma.put(act, sumSigma / counter);
        }

        Map<NodeWithActivity, Double> newOppoWeights = new HashMap<>();
        for (Map.Entry<NodeWithActivity, Double> entry : opportunityWeights.entrySet()) {
            double newSigma = entry.getValue() / meanSigma.get(entry.getKey().getActivity());
            newOppoWeights.put(entry.getKey(), newSigma);
        }

        return newOppoWeights;
    }

    /**
     * Performs min-max scaling on opportunity weights across all zones for each activity type.
     * Scales values to [0, 1] range: scaled = (value - min) / (max - min)
     *
     * @param opportunityWeights Map of zone -> (activity -> weight)
     * @return New map with scaled weights
     */
    public static Map<String, Map<Activity, Double>> minMaxScaleAcrossZones(
            Map<String, Map<Activity, Double>> opportunityWeights) {

        logger.info("Performing min-max scaling across {} zones", opportunityWeights.size());

        Map<Activity, Double> minValues = new HashMap<>();
        Map<Activity, Double> maxValues = new HashMap<>();

        for (Map<Activity, Double> activityWeights : opportunityWeights.values()) {
            for (Map.Entry<Activity, Double> entry : activityWeights.entrySet()) {
                Activity activity = entry.getKey();
                Double weight = entry.getValue();
                minValues.merge(activity, weight, Math::min);
                maxValues.merge(activity, weight, Math::max);
            }
        }

        Map<String, Map<Activity, Double>> scaledWeights = new HashMap<>();

        for (Map.Entry<String, Map<Activity, Double>> zoneEntry : opportunityWeights.entrySet()) {
            Map<Activity, Double> scaledActivityWeights = new HashMap<>();

            for (Map.Entry<Activity, Double> actEntry : zoneEntry.getValue().entrySet()) {
                Activity activity = actEntry.getKey();
                double weight = actEntry.getValue();
                double min = minValues.get(activity);
                double max = maxValues.get(activity);
                double scaled = (max - min > 1e-10) ? (weight - min) / (max - min) : 0.5;

                scaledActivityWeights.put(activity, scaled);
            }

            scaledWeights.put(zoneEntry.getKey(), scaledActivityWeights);
        }

        logger.info("Min-max scaling complete. Activity ranges: {}",
                minValues.keySet().stream()
                        .map(a -> a + "[" + minValues.get(a) + ", " + maxValues.get(a) + "]")
                        .toList());

        return scaledWeights;
    }

    public static Map<String, Map<Activity, Double>> normalizeAcrossZonesPerActivity(
            Map<String, Map<Activity, Double>> originalWeights) {

        Map<String, Map<Activity, Double>> opportunityWeights = minMaxScaleAcrossZones(originalWeights);
        logger.info("Normalizing opportunity weights across zones per activity (sum to 1).");

        Map<Activity, Double> totals = new HashMap<>();
        for (Map<Activity, Double> activityWeights : opportunityWeights.values()) {
            for (Map.Entry<Activity, Double> entry : activityWeights.entrySet()) {
                totals.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
        }

        Map<String, Map<Activity, Double>> normalized = new HashMap<>();
        for (Map.Entry<String, Map<Activity, Double>> zoneEntry : opportunityWeights.entrySet()) {
            Map<Activity, Double> normActivityWeights = new HashMap<>();

            for (Map.Entry<Activity, Double> actEntry : zoneEntry.getValue().entrySet()) {
                Activity activity = actEntry.getKey();
                double weight = actEntry.getValue();
                double total = totals.getOrDefault(activity, 0.0);
                double normWeight = (total > 0.0) ? weight / total : 0.0;

                normActivityWeights.put(activity, normWeight);
            }

            normalized.put(zoneEntry.getKey(), normActivityWeights);
        }

        logger.info("Normalization complete. Activities: {}", totals.keySet());
        return normalized;
    }

    public static void main(String[] args) {
        var data = LoadOpportunityWeights.load("data/zone_purpose_area.csv");
        System.out.println(data.size());
    }

    /**
     * Performs min-max scaling on node opportunity weights per activity type.
     * Scales values to [0, 1] range: scaled = (value - min) / (max - min)
     *
     * @param weights Map of node -> weight
     * @return New map with scaled weights
     */
    public static Map<NodeWithActivity, Double> minMaxScaleAcrossNodes(
            Map<NodeWithActivity, Double> weights) {

        logger.info("Performing min-max scaling across {} nodes", weights.size());

        Map<Activity, Double> minValues = new HashMap<>();
        Map<Activity, Double> maxValues = new HashMap<>();

        for (Map.Entry<NodeWithActivity, Double> entry : weights.entrySet()) {
            Activity activity = entry.getKey().getActivity();
            Double weight = entry.getValue();
            minValues.merge(activity, weight, Math::min);
            maxValues.merge(activity, weight, Math::max);
        }

        Map<NodeWithActivity, Double> scaled = new HashMap<>();
        for (Map.Entry<NodeWithActivity, Double> entry : weights.entrySet()) {
            NodeWithActivity node = entry.getKey();
            Activity activity = node.getActivity();
            double weight = entry.getValue();
            double min = minValues.get(activity);
            double max = maxValues.get(activity);
            double scaledWeight = (max - min > 1e-10) ? (weight - min) / (max - min) : 0.5;
            scaled.put(node, scaledWeight);
        }

        logger.info("Min-max scaling complete. Activity ranges: {}",
                minValues.keySet().stream()
                        .map(a -> a + "[" + minValues.get(a) + ", " + maxValues.get(a) + "]")
                        .toList());

        return scaled;
    }
/**
 * Scales node opportunity weights by the per-activity maximum: scaled = value / max.
 * Best zone for each activity -> 1; others keep their true fraction (floor stays positive,
 * not pinned to 0 as in min-max). Within-activity ratios are preserved.
 */
public static Map<NodeWithActivity, Double> divideByMaxAcrossNodes(
        Map<NodeWithActivity, Double> weights) {

    logger.info("Scaling opportunity weights by per-activity max across {} nodes", weights.size());

    Map<Activity, Double> maxValues = new HashMap<>();
    for (Map.Entry<NodeWithActivity, Double> entry : weights.entrySet()) {
        maxValues.merge(entry.getKey().getActivity(), entry.getValue(), Math::max);
    }

    Map<NodeWithActivity, Double> scaled = new HashMap<>();
    for (Map.Entry<NodeWithActivity, Double> entry : weights.entrySet()) {
        Activity activity = entry.getKey().getActivity();
        double max = maxValues.get(activity);
        double scaledWeight = (max > 1e-10) ? entry.getValue() / max : 0.0;
        scaled.put(entry.getKey(), scaledWeight);
    }

    logger.info("Divide-by-max scaling complete. Activity maxima: {}",
            maxValues.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .toList());

    return scaled;
}
/**
 * Mean opportunity weight over all (expanded) nodes. This is sigma-bar; the exact
 * length-neutralizing constant for the staying weight is c = -ln(sigma-bar), applied
 * per stage. Average is over the same node set the proposal draws stages from.
 */
public static double meanSigma(Map<NodeWithActivity, Double> weights) {
    if (weights.isEmpty()) {
        throw new IllegalArgumentException("empty opportunity weights");
    }
    double sum = 0.0;
    for (double w : weights.values()) {
        sum += w;
    }
    return sum / weights.size();
}
}