package renate.cases;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;
import renate.model.Activity;

public class RunnerOptions {
    @Option(names = "--name", defaultValue = "null") public String name;

    // --- Paths ---
    @Option(names = "--scenarioDir") public String scenarioDir;
    @Option(names = "--outputDir", defaultValue = "output/") public String outputDir;

    // --- Run config ---
    @Option(names = "--maxProcesses", defaultValue = "-1") public int maxProcesses;
    @Option(names = "--maxIter") public Integer maxIter;
    @Option(names = "--seed", defaultValue = "1084") public long seed;
    @Option(names = "--timeBinSize", defaultValue = "1.0") public double timeBinSize;
    @Option(names = "--horizon", defaultValue = "24.0") public double horizon;
    @Option(names = "--msgInterval", defaultValue = "-1") public int msgInterval;
    @Option(names = "--stationarity-tolerance", defaultValue = "0.1") public double stationarity_tolerance;
    @Option(names = "--min-number-of-stable-samples", defaultValue = "0",
            description = "Minimum number of msgInterval-spaced samples to draw after stationarity is detected (0 = terminate immediately on stationarity)")
    public int min_number_of_stable_samples;

    // Prior 
    @Option(names = "--binomial-prior") public boolean binomialPrior;
    @Option(names = "--enforce-correct-home-location") public boolean enforceCorrectHomeLocation;
    @Option(names = "--enforce-unique-home-location") public boolean enforce_unique_home_location;
    @Option(names = "--strictly-periodic-schedule") public boolean strictly_periodic_schedule;

    @Option(names = "--withKernelWeights") public boolean withKernelWeights;
    // --- Traveler kernel ---
    @Option(names = "--indication-traveler-kernel") public boolean indicatorTravelerKernel;
    @Option(names = "--lAge", defaultValue = "3.0") public double lAge;
    @Option(names = "--lInc", defaultValue = "75.0") public double lInc;
    @Option(names = "--eta", defaultValue = "0.2") public double eta;
    @Option(names = "--travelerKernelThreshold", defaultValue = "1e-6") public double kernelThreshold;
    @Option(names = "--travelerMixWeight", defaultValue = "1.0") public double travelerMixWeight;

    // --- Location Kernel --- 
    @Option(names = "--jaccard-activity-location-kernel") public boolean jaccard_activity_location_kernel;
    @Option(names = "--jaccard-activity-location-kernel-bandwidth", defaultValue = "1.0") public double jaccard_activity_location_kernel_bandwidth;
    
    // --- Schedule kernel ---
    @Option(names = "--nStopsBandwidth", defaultValue = "0") public double nStopsBandwidth;
    @Option(names = "--tttBandwidth", defaultValue = "0") public double tttBandwidth;
    @Option(names = "--scheduleOverlapBandwidth", defaultValue = "0") public double scheduleOverlapBandwidth;
    @Option(names = "--kernelFactor", defaultValue = "1.0") public double kernelFactor;
    @Option(names = "--subtractPartition") public boolean subtractPartition;
    @Option(names = "--scheduleMixWeight",  defaultValue = "1.0") public double schedule_mixing_weight;

    @Option(names = "--nStopsWeight", defaultValue = "1.0") public double n_stops_weight;
    @Option(names = "--tttWeight", defaultValue = "1.0") public double ttt_weight;
    @Option(names = "--scheduleOverlapWeight", defaultValue = "1.0") public double schedule_overlap_weight;

    @Option(names = "--location-kernel-weight", defaultValue = "1.0") public double location_kernel_weight;

    // ==== Activity Median Durations ===
    @Option(names = "--schedule-overlap-c") public Double schedule_overlap_c = null;
    @Option(names = "--median-duration") public Map<Activity, Double> median_durations = new EnumMap<>(Activity.class);

    // ==== Unique activity nodes ====
    // Activities restricted to a single node per round trip: only the visited node with the
    // highest opportunity weight accrues, in the staying weight and the schedule overlap
    // kernel alike. Visiting other nodes is allowed, it just earns nothing.
    // Usage: --unique-activity-node WORK=true
    @Option(names = "--unique-activity-node") public Map<Activity, Boolean> unique_activity_nodes = new EnumMap<>(Activity.class);

    // --- AMAL Optional weights ---
    @Option(names = "--withStayingWeight") public boolean with_staying_weight;
    @Option(names = "--geometricMeanStayingWeight") public boolean geometric_mean_staying_weight_flag;
    @Option(names = "--withCenterStay") public boolean with_center_stay;
    @Option(names = "--stayMixingWeight", defaultValue="1.0") public double stay_mixing_weight;
    @Option(names = "--staying-weight-exponent", defaultValue = "1.0") public double staying_weight_exponent;
    
    @Option(names = "--normalizeWeightsAcrossNodes") public boolean normalize_weights_across_nodes;
    @Option(names = "--normalize-by-mean-sigma") public boolean normalize_by_mean_sigma;

    @Option(names = "--withMovementWeight") public boolean with_movement_weight;
    @Option(names = "--geometricMeanMovementWeight") public boolean geometric_mean_movement_weight;
    @Option(names = "--traceNormalizedMovementWeight") public boolean trace_normalized_movement_weight;
    @Option(names = "--moveMixingWeight", defaultValue = "1.0") public double move_mixing_weight;
    @Option(names = "--with-raw-movement-counts") public boolean raw_counts_flag;
    @Option(names = "--movement-weight-exponent", defaultValue = "1.0") public double movement_weight_exponent;

    // --- Warm start ---
    @Option(names = "--warmStartFile") public String warmStartFile;

    // --- Output ---
    @Option(names = "--writeFinalState", negatable = true) public boolean writeFinalState = true;
    @Option(names = "--verbose") public boolean verbose;

    // --- Plotting ---
    @Option(names = "--output-suffix", defaultValue=".pdf") public String output_suffix;
    @Option(names = "--no-plot-weights", negatable = true, defaultValue = "true") public boolean plot_weights = true;
    @Option(names = "--no-plot-convergence-stats", negatable = true, defaultValue = "true") public boolean plot_convergence_stats = true;
    @Option(names = "--no-plot-evaluation-plots", negatable = true, defaultValue = "true") public boolean plot_evaluation_plots = true;
    @Option(names = "--no-plot-survey-time-structure", negatable = true, defaultValue = "true") public boolean plot_survey_time_structure = true;
    @Option(names = "--no-activity-location-plotter", negatable = true, defaultValue = "true") public boolean activity_location_plotter = true;
    @Option(names = "--no-plot-activity-duration-reproduction", negatable = true, defaultValue = "true") public boolean plot_activity_duration_reproduction = true;
    @Option(names = "--no-plot-kernel-matrices", negatable = true, defaultValue = "true") public boolean plot_kernel_matrices = true;
    @Option(names = "--no-plot-reproduction", negatable = true, defaultValue = "true") public boolean plot_reproduction = true;
    @Option(names = "--no-plot-education-diagnostics", negatable=true, defaultValue = "true") public boolean plot_education_diagnostics = true;
    
    // --- Population-level plots (run once, after partitions are merged) ---
    @Option(names = "--no-plot-od-and-transitions", negatable = true, defaultValue = "true") public boolean plot_od_and_transitions = true;
    @Option(names = "--no-plot-population-reproduction", negatable = true, defaultValue = "true") public boolean plot_population_reproduction = true;
    @Option(names = "--no-plot-individual-reproduction", negatable = true, defaultValue = "true") public boolean plot_individual_reproduction = true;
    @Option(names = "--measurementStatesXml") public String measurementStatesXml;
    @Option(names = "--validationStatesXml") public String validationStatesXml;
    @Option(names = "--convergenceStatsFilename", defaultValue = "convergenceStats.tsv") public String convergenceStatsFilename;

    //--debugging 
    @Option(names="--wanted-agents", split=",") public List<String> wanted_agents;
    
    @ArgGroup(exclusive = true, multiplicity = "0..1")
    public ExclusiveRespondentSelection respondentSelection;
    
    public static class ExclusiveRespondentSelection {
        @Option(names = "--n-respondents") public Integer n_respondents;
        @Option(names = "--wanted-respondents", split = ",") public List<String> wanted_respondents;
    }


}