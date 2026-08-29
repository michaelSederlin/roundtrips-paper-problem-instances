package synthesis;

import java.nio.file.Path;
import java.util.Map;
import picocli.CommandLine.Option;

public class Options {
    @Option(names = "--maxIter", defaultValue = "20e6") public int max_iter;
    @Option(names = "--population-size", defaultValue = "1000") public int population_size;
    @Option(names = "--preferences-path") public Path preferences_path;
    @Option(names = "--output-dir", defaultValue = "output/") public Path output_dir;
    @Option(names = "--horizon", defaultValue = "24.0") public double horizon;
    @Option(names = "--time-bin-size", defaultValue = "1.0") public double time_bin_size;
    @Option(names = "--grid-size", defaultValue = "5") public int grid_size;
    @Option(names = "--max-processes", defaultValue = "-1") public int max_processes;
    @Option(names = "--base-seed") public Integer base_seed;
    @Option(names = "--grid-world-seed") public Integer grid_world_seed;
    @Option(names = "--scenario-seed") public Integer scenario_seed;
    @Option(names = "--opportunities-path") public Path opportunities_path;
    @Option(names = "--population-configs-path") public Path population_configs_path;

    @Option(names = "--work-open") public Double work_open;
    @Option(names = "--work-close") public Double work_close;
    @Option(names = "--min-work-duration") public Double min_work_duration;

    @Option(names = "--education-open") public Double education_open;
    @Option(names = "--education-close") public Double education_close;
    @Option(names = "--min-education-duration") public Double min_education_duration;

    @Option(names = "--other-open") public Double other_open;
    @Option(names = "--other-close") public Double other_close;
    @Option(names = "--min-other-duration") public Double min_other_duration;

    @Option(names = "--home-open") public Double home_open;
    @Option(names = "--home-close") public Double home_close;
    @Option(names = "--min-home-duration") public Double min_home_duration;

    @Option(names = "--time-use-mixing-weight", defaultValue = "1.0") public double time_use_mixing_weight;
    @Option(names = "--enforce-correct-home-location", defaultValue = "true", negatable = true) public boolean enforce_correct_home_location = true;

    @Option(names = "--variance-tolerance", defaultValue = "0.1") public Double variance_tolerance;
    @Option(names = "--mean-tolerance", defaultValue = "0.1") public Double mean_tolerance;

    public void save() {
    String fp = this.output_dir.resolve("options.log").toString();
    try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(fp))) {
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
        throw new RuntimeException("Failed to save args to " + fp, e);
    }
}
}