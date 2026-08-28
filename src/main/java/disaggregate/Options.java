package disaggregate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;
import common.Activity;

public class Options {
    @Option(names = "--name", defaultValue = "null") public String name;
    @Option(names = "--scenario-dir") public String scenario_dir;
    @Option(names = "--output-dir", defaultValue = "output/") public String output_dir;
    @Option(names = "--convergence-stats-filename", defaultValue = "convergenceStats.tsv") public String convergence_stats_filename;
    @Option(names = "--enforce-unique-home-location") public boolean enforce_unique_home_location;
    @Option(names = "--n-respondents") public Integer n_respondents;

    // --- Run config ---
    @Option(names = "--max-processes", defaultValue = "-1") public int max_processes;
    @Option(names = "--max-number-of-iterations") public Integer max_number_of_iterations;
    @Option(names = "--seed", defaultValue = "1084") public long seed;
    @Option(names = "--time-bin-size", defaultValue = "1.0") public double time_bin_size;
    @Option(names = "--horizon", defaultValue = "24.0") public double horizon;
    @Option(names = "--msg-interval", defaultValue = "-1") public int msg_interval;
    @Option(names = "--stationarity-tolerance", defaultValue = "0.1") public double stationarity_tolerance;
    @Option(names = "--min-number-of-stable-samples", defaultValue = "0",
            description = "Minimum number of msgInterval-spaced samples to draw after stationarity is detected (0 = terminate immediately on stationarity)")
    public int min_number_of_stable_samples;

    // Prior
    @Option(names = "--enforce-correct-home-location") public boolean enforce_correct_home_location;

    @Option(names = "--with-kernel-weights") public boolean with_kernel_weights;
    @Option(names = "--kernel-mixing-weight", defaultValue = "1.0") public double kernel_mixing_weight;

    // --- Traveler kernel ---
    @Option(names = "--lambda-age", defaultValue = "3.0") public double lambda_age;
    @Option(names = "--lambda-income", defaultValue = "75.0") public double lambda_income;
    @Option(names = "--eta", defaultValue = "0.2") public double eta;
    @Option(names = "--traveler-kernel-threshold", defaultValue = "1e-6") public double traveler_kernel_threshold;
    @Option(names = "--traveler-kernel-mixing-weight", defaultValue = "1.0") public double traveler_kernel_mixing_weight;

    // --- Schedule kernel ---
    @Option(names = "--n-stops-bandwidth", defaultValue = "0") public double n_stops_bandwidth;
    
    @Option(names = "--n-stops-mixing-weight", defaultValue = "1.0") public double n_stops_mixing_weight;
    @Option(names = "--schedule-overlap-mixing-weight", defaultValue = "1.0") public double schedule_overlap_mixing_weight;
    @Option(names = "--schedule-kernel-mixing-weight",  defaultValue = "1.0") public double schedule_kernel_mixing_weight;

    // ==== Activity Median Durations ===
    @Option(names = "--median-duration") public Map<Activity, Double> median_durations = new EnumMap<>(Activity.class);

    // ==== Unique activity nodes ====
    // Activities restricted to a single node per round trip: only the visited node with the
    // highest opportunity weight accrues, in the staying weight and the schedule overlap
    // kernel alike. Visiting other nodes is allowed, it just earns nothing.
    // Usage: --unique-activity-node WORK=true
    @Option(names = "--unique-activity-node") public Map<Activity, Boolean> unique_activity_nodes = new EnumMap<>(Activity.class);

    // --- AMAL Optional weights ---
    @Option(names = "--with-staying-weight") public boolean with_staying_weight;
    @Option(names = "--stay-mixing-weight", defaultValue="1.0") public double stay_mixing_weight;
    @Option(names = "--normalize-weights-across-nodes") public boolean normalize_weights_across_nodes;

    @Option(names = "--with-movement-weight") public boolean with_movement_weight;
    @Option(names = "--move-mixing-weight", defaultValue = "1.0") public double move_mixing_weight;
    @Option(names = "--with-raw-movement-counts") public boolean raw_counts_flag;

    // --- Warm start ---
    @Option(names = "--warm-start-file") public String warm_start_file;

    // --- Output ---
    @Option(names = "--write-final-state", negatable = true) public boolean write_final_state = true;

    public void saveArgs(String path) {
    try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(path))) {
        for (var f : this.getClass().getDeclaredFields()) {
            var opt = f.getAnnotation(Option.class);
            if (opt == null) continue;
            f.setAccessible(true);
            Object val = f.get(this);
            String name = opt.names()[0];
            if (val instanceof Map<?, ?> map) {
                for (var e : map.entrySet()) pw.println(name + " " + e.getKey() + "=" + e.getValue());
            } else {
                pw.println(name + "=" + val);
            }
        }
    } catch (Exception e) {
        throw new RuntimeException("Failed to save args to " + path, e);
    }
}
}