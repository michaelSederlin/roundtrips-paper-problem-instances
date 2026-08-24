package disaggregate;

import picocli.CommandLine.Option;

/**
 * Command line options for the minimal AMAL runner.
 *
 * A trimmed copy of {@code renate.cases.RunnerOptions}: only what the paper's
 * experiments actually configure survives here. Numeric defaults are the values
 * reported in the paper, so {@code amal-paper-arguments.txt} mostly has to point
 * at a scenario and switch the three weights on.
 *
 * Mixed into {@link Runner} with picocli's {@code @Mixin}.
 */
public class Options {

    // --- Run identity ---
    @Option(names = "--name", defaultValue = "amal",
            description = "Sub-directory of --outputDir that this run writes into.")
    public String name;

    // --- Paths ---
    @Option(names = "--scenario-dir", required = true,
            description = "Directory holding nodes.tsv, edges.tsv, upbd.csv, mddr.csv"
                    + " and (depending on the weights) opportunity_weights.csv, od_matrix.tsv.")
    public String scenario_dir;

    @Option(names = "--outputDir", defaultValue = "output/")
    public String outputDir;

    @Option(names = "--warm-start-file",
            description = "finalState.json of an earlier run to start the chain from."
                    + " Indices are global: partition k reads the slice of the state that"
                    + " corresponds to its own agents.")
    public String warm_start_file;

    // --- Run config ---
    @Option(names = "--max-iter",
            description = "Iteration cap. Unset means the chain runs until the block-average"
                    + " convergence criterion is met.")
    public Integer max_iter;

    @Option(names = "--seed", defaultValue = "1084") public long seed;
    @Option(names = "--time-bin-size", defaultValue = "1.0") public double time_bin_size;
    @Option(names = "--horizon", defaultValue = "24.0") public double horizon;

    @Option(names = "--msg-interval", defaultValue = "-1",
            description = "Iterations between logged/recorded samples; -1 means maxIter/100,"
                    + " or 5000 when --maxIter is unset.")
    public int msg_interval;

    @Option(names = "--max-processes", defaultValue = "-1",
            description = "Number of partitions sampled in parallel; -1 means one per available core."
                    + " Population weights are evaluated within a partition, so this is a modelling"
                    + " choice, not only a speed knob.")
    public int max_processes;

    @Option(names = "--stationarity-tolerance", defaultValue = "0.2",
            description = "Relative-variance and standardized-mean tolerance of the block-average"
                    + " convergence criterion.")
    public double stationarity_tolerance;

    @Option(names = "--n-respondents",
            description = "Draw this many survey respondents at random (seeded by --seed)."
                    + " Unset means every person in the survey is a respondent.")
    public Integer n_respondents;

    @Option(names = "--enforce-correct-home-location",
            description = "Reject states in which an agent's home location differs from the survey's.")
    public boolean enforceCorrectHomeLocation;

    // --- Kernel weight (survey responses) ---
    @Option(names = "--kernel-mixing-weight", defaultValue = "1.0") public double kernel_mixing_weight;

    // Traveler kernel
    @Option(names = "--lambda-age", defaultValue = "3.0") public double lAge;
    @Option(names = "--lambda-income", defaultValue = "75.0") public double lInc;
    @Option(names = "--eta", defaultValue = "0.3") public double eta;
    @Option(names = "--socio-demographic-mixing-weight", defaultValue = "1.0") public double socio_demographic_mixing_weight;

    // Schedule kernel
    @Option(names = "--n-stops-bandwidth", defaultValue = "0.3") public double n_stops_bandwidth;
    @Option(names = "--ttt-bandwidth", defaultValue = "0.5") public double ttt_bandwidth;
    @Option(names = "--schedule-overlap-bandwidth", defaultValue = "0.5") public double schedule_overlap_bandwidth;
    @Option(names = "--schedule_mixing_weight", defaultValue = "1.0") public double schedule_mixing_weight;

    @Option(names = "--n-stops-mixing-weight", defaultValue = "1.0",
            description = "Weight of the number-of-stops kernel inside the schedule kernel; 0 switches it off.")
    public double n_stops_mixing_weight;

    @Option(names = "--schedule-overlap-mixing-weight", defaultValue = "2.0",
            description = "Weight of the schedule-overlap kernel inside the schedule kernel; 0 switches it off.")
    public double schedule_overlap_mixing_weight;


    @Option(names = "--stayMixingWeight", defaultValue = "2.0") public double stayMixingWeight;
    @Option(names = "--moveMixingWeight", defaultValue = "2.0") public double moveMixingWeight;


    @Option(names = "--verbose",
            description = "Let the sampler print its own per-iteration progress messages.")
    public boolean verbose;

    /** Iterations between logged samples, resolving the -1 sentinel. */
    public int effectiveMsgInterval() {
        if (this.msgInterval >= 0) {
            return this.msgInterval;
        }
        return this.maxIter != null ? Math.max(1, this.maxIter / 100) : 5000;
    }
}
