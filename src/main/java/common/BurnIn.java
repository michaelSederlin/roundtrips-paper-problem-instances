package common;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Port of burn_in.py. Reads convergenceStats.tsv (written by
 *  BlockAverageTerminationCriterion), in the same raw MCMC-iteration units as the
 *  `iteration` attribute in states.xml. */
public final class BurnIn {

    private BurnIn() {}

    /** (burnIn, converged); burnIn == null means "no usable value". */
    public record BurnInResult(Integer burnIn, boolean converged) {}

    /** (iterations, converged). */
    public record PostBurnIn(int[] iterations, boolean converged) {}

    /** Parse convergenceStats.tsv into rows sorted by iteration, or null (with a
     *  warning) if the file is missing, unparsable, empty, or lacks burnInIteration. */
    private static List<Map<String, String>> loadConvergenceDf(Path fp) {
        if (!Files.exists(fp)) {
            System.out.println("WARNING: " + fp + " not found; falling back to fixed --window.");
            return null;
        }
        List<Map<String, String>> rows;
        try {
            rows = readTsv(fp);
        } catch (IOException | RuntimeException exc) {
            System.out.println("WARNING: could not parse " + fp + " (" + exc + "); falling back to fixed --window.");
            return null;
        }
        if (rows.isEmpty() || !rows.get(0).containsKey("burnInIteration")) {
            System.out.println("WARNING: " + fp + " has no usable rows (run may have terminated before "
                + "the minimum sample count needed for a convergence check); falling back to fixed --window.");
            return null;
        }
        rows.sort(Comparator.comparingDouble(r -> Double.parseDouble(r.get("iteration").trim())));
        return rows;
    }

    /** Validated burn-in cut and convergence flag. The run's own cut is the FIRST
     *  stabilized=true row (the latch), not the last row (which after post-latch
     *  drift of the noisy block test is often stabilized=false again). */
    public static BurnInResult burnInAndConverged(Path fp) {
        List<Map<String, String>> df = loadConvergenceDf(fp);
        if (df == null) return new BurnInResult(null, false);

        List<Map<String, String>> stabilizedRows = new ArrayList<>();
        for (Map<String, String> row : df) {
            if (isTrue(row.get("stabilized")) && isNumeric(row.get("burnInIteration"))) {
                stabilizedRows.add(row);
            }
        }

        if (!stabilizedRows.isEmpty()) {
            Map<String, String> latch = stabilizedRows.get(0); // first stabilized row = the latch
            int burnIn = (int) Double.parseDouble(latch.get("burnInIteration").trim());
            boolean lastStabilized = isTrue(df.get(df.size() - 1).get("stabilized"));
            if (!lastStabilized) {
                System.out.println("INFO: " + fp.getParent() + " stabilized at iteration "
                    + (int) Double.parseDouble(latch.get("iteration").trim()) + " (burnInIteration=" + burnIn
                    + ") but was not stable at its final row (post-latch drift of the block test); latched cut retained.");
            }
            return new BurnInResult(burnIn, true);
        }

        // No validated stabilization anywhere -> fall back to the last row's guess.
        String burnStr = df.get(df.size() - 1).get("burnInIteration");
        if (!isNumeric(burnStr)) {
            System.out.println("WARNING: last row of " + fp + " has no burnInIteration; falling back to fixed --window.");
            return new BurnInResult(null, false);
        }
        int burnIn = (int) Double.parseDouble(burnStr.trim());
        System.out.println("WARNING: " + fp.getParent() + " has no stabilized row (never reached stationarity); "
            + "using best-guess burnInIteration=" + burnIn + " anyway.");
        return new BurnInResult(burnIn, false);
    }

    /** Back-compat wrapper returning just the validated burn-in cut (or null). */
    public static Integer readBurnInIteration(Path fp) {
        return burnInAndConverged(fp).burnIn();
    }

    /** Iterations to aggregate over for a single partition, plus a converged flag.
     *  All logged iterations >= the burn-in cut if usable and non-empty, else the
     *  last `window` logged iterations. */
    public static PostBurnIn postBurnInIterations(int[] allIterations, Path fp, int window) {
        BurnInResult r = burnInAndConverged(fp);
        if (r.burnIn() != null) {
            int cut = r.burnIn();
            int[] post = Arrays.stream(allIterations).filter(x -> x >= cut).toArray();
            if (post.length > 0) return new PostBurnIn(post, r.converged());
            System.out.println("WARNING: no logged iterations >= burnInIteration=" + cut
                + " (msg-interval coarser than the remaining post-burn-in run?); falling back to fixed --window=" + window + ".");
        }
        int[] fallback = allIterations.length >= window
            ? Arrays.copyOfRange(allIterations, allIterations.length - window, allIterations.length)
            : allIterations;
        return new PostBurnIn(fallback, false);
    }

    /** Thin wrapper over postBurnInIterations for callers that do not need the flag. */
    public static int[] selectPostBurnInOrWindow(int[] allIterations, Path fp, int window) {
        return postBurnInIterations(allIterations, fp, window).iterations();
    }

    /** Trim every partition's post-burn-in array to a common count N (min across
     *  partitions), keeping each partition's most recent N iterations. Returns
     *  {label: kept iterations}. */
    public static Map<String, Set<Integer>> equalizePostBurnIn(
            Map<String, int[]> arraysByLabel, Map<String, Boolean> convergedByLabel) {
        if (arraysByLabel.isEmpty()) return Map.of();

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> e : arraysByLabel.entrySet()) {
            counts.put(e.getKey(), e.getValue().length);
        }
        int n = Collections.min(counts.values());
        String setter = counts.entrySet().stream()
            .min(Comparator.comparingInt(Map.Entry::getValue)).orElseThrow().getKey();
        double medianCount = median(counts.values());

        System.out.println("Equalizing post-burn-in samples across " + counts.size() + " partition(s) to N=" + n
            + " (set by partition '" + setter + "'); per-partition counts: " + new TreeMap<>(counts) + ".");
        if (!convergedByLabel.getOrDefault(setter, false)) {
            System.out.println("WARNING: partition '" + setter + "' sets the common sample count N=" + n
                + " but has no validated stabilization (no stabilized row in its convergenceStats.tsv); "
                + "the equalized sample count may be below min_stationary_intervals.");
        }
        if (medianCount > 0 && n < 0.5 * medianCount) {
            System.out.printf("WARNING: common sample count N=%d is well below the across-partition median of %.0f; "
                + "partition '%s' is an outlier dragging the analysis sample count down.%n", n, medianCount, setter);
        }

        Map<String, Set<Integer>> result = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> e : arraysByLabel.entrySet()) {
            int[] a = e.getValue();
            int[] kept = a.length >= n ? Arrays.copyOfRange(a, a.length - n, a.length) : a;
            Set<Integer> s = new HashSet<>();
            for (int v : kept) s.add(v);
            result.put(e.getKey(), s);
        }
        return result;
    }

    // ---- helpers ----

    private static boolean isTrue(String s) {
        return s != null && s.trim().equalsIgnoreCase("true");
    }

    private static boolean isNumeric(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.isEmpty() || t.equalsIgnoreCase("nan")) return false;
        try { Double.parseDouble(t); return true; } catch (NumberFormatException e) { return false; }
    }

    private static double median(Collection<Integer> values) {
        List<Integer> list = new ArrayList<>(values);
        Collections.sort(list);
        int m = list.size();
        if (m == 0) return 0.0;
        return (m % 2 == 1) ? list.get(m / 2) : (list.get(m / 2 - 1) + list.get(m / 2)) / 2.0;
    }

    private static List<Map<String, String>> readTsv(Path fp) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader r = Files.newBufferedReader(fp, StandardCharsets.UTF_8)) {
            String headerLine = r.readLine();
            if (headerLine == null) return rows;
            String[] header = headerLine.split("\t", -1);
            String line;
            while ((line = r.readLine()) != null) {
                String[] cells = line.split("\t", -1);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < header.length; i++) {
                    row.put(header[i], i < cells.length ? cells[i] : "");
                }
                rows.add(row);
            }
        }
        return rows;
    }
}