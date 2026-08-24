package disaggregate.model.weights;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import disaggregate.model.ActivityNode;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.utils.misc.Tuple;
import se.vti.utils.misc.metropolishastings.MHWeight;


public class MovementWeight implements MHWeight<MultiRoundTrip<ActivityNode>> {
    static final Logger logger = LogManager.getLogger(MovementWeight.class);
    // --- Variation Flags --- 
    private boolean geometricMean = false;
    private double exponent = 1.0;
    private boolean traceNormalized = false;

    private final double epsilon;
    private final Map<Tuple<String, String>, Double> logTransitionCounts;
    private final Map<String, Double> allDestinationLogTransitionCounts;

    private double c = 0.0;

    // Global-normalization fix: logZ[J] = log(tr(P^J)) - J*log(L).
    private int maxStages;        // = D (number of time bins); -1 if not supplied
    private double[] logZ;        // null if maxStages < 0
    private boolean rawCountsFlag = false;
    private Map<Tuple<String, String>, Double> logTransitionWeight;

    // -------------------- CONSTRUCTORS --------------------
    public MovementWeight(Map<Tuple<String, String>, Double> ODMatrix, double epsilon) {
        this.epsilon = epsilon;
        this.logTransitionCounts = precomputeLogTransitionCounts(ODMatrix);
        this.allDestinationLogTransitionCounts = precomputeAllDestinationLogTransitionCounts(ODMatrix);
        this.logTransitionWeight = precomputeLogTransitionWeight();
    }

    public MovementWeight(Map<Tuple<String, String>, Double> transitionProbabilities) {
        this(transitionProbabilities, 1e-6);
    }

    // -------------------- CONFIGURATION --------------------
    public MovementWeight withExponent(double exponent) {
        this.exponent = exponent;
        this.logTransitionWeight = precomputeLogTransitionWeight();
        return this;
    }

    public MovementWeight withGeometricMean() {
        if (traceNormalized) throw new IllegalArgumentException("Turning on geometric mean but trace normalization is also enabled");
        geometricMean = true;
        return this;
    }

    public MovementWeight withRawCounts() {
        this.rawCountsFlag = true;
        this.logTransitionWeight = precomputeLogTransitionWeight();
        logger.info("Using raw counts");
        return this;
    }

    public MovementWeight withTraceNormalization(int maxStages) {
        throw new UnsupportedOperationException("Trace Normalization is not implemented (needs to be updated for other code changes)");
        // if (geometricMean) throw new IllegalArgumentException("Turning on trace normalization but geometric mean has already been enabled");
        // this.maxStages = maxStages;
        // traceNormalized = true;
        // this.logZ = (maxStages >= 0) ? precomputeLogPartition(maxStages) : null;
        // return this;
    }

    public MovementWeight withC(double cVal) {
        this.c = cVal;
        return this;
    }

    @Override
    public double logWeight(MultiRoundTrip<ActivityNode> state) {
        return state.getSummary(MovementSummary.class).getTotalLW();
    }

    private Map<Tuple<String, String>, Double> precomputeLogTransitionWeight() {
        Map<Tuple<String, String>, Double> w = new HashMap<>();
        for (Map.Entry<Tuple<String, String>, Double> e: logTransitionCounts.entrySet()) {
            double logNum = e.getValue();
            double val = rawCountsFlag
                ? exponent * logNum
                : exponent * (logNum - allDestinationLogTransitionCounts.get(e.getKey().getA()));
            w.put(e.getKey(), val);
        }
        return w;
    }
    
    private Map<Tuple<String, String>, Double> precomputeLogTransitionCounts(
            Map<Tuple<String, String>, Double> transitionProbabilities) {
        Map<Tuple<String, String>, Double> logProbabilities = new HashMap<>();
        for (Map.Entry<Tuple<String, String>, Double> entry : transitionProbabilities.entrySet()) {
            logProbabilities.put(entry.getKey(), Math.log(entry.getValue()));
        }
        return logProbabilities;
    }

    private Map<String, Double> precomputeAllDestinationLogTransitionCounts(
        Map<Tuple<String, String>, Double> transitionProbabilities) {
        Map<String, Double> probabilities = new HashMap<>();
        for (Map.Entry<Tuple<String, String>, Double> entry : transitionProbabilities.entrySet()) {
            Tuple<String, String> transition = entry.getKey();
            Double currentProb = probabilities.getOrDefault(transition.getA(), 0.0);
            probabilities.put(transition.getA(), currentProb + entry.getValue());
        }
        for (Map.Entry<String, Double> entry : probabilities.entrySet()) {
            probabilities.put(entry.getKey(), Math.log(entry.getValue() + epsilon));
        }
        return probabilities;
    }

    // -------------------- PRECOMPUTATION: PER-LENGTH LOG PARTITION --------------------
    private double[] precomputeLogPartition(int maxStages) {
        LinkedHashSet<String> locSet = new LinkedHashSet<>();
        for (Tuple<String, String> key : logTransitionCounts.keySet()) {
            locSet.add(key.getA());
            locSet.add(key.getB());
        }
        List<String> locs = new ArrayList<>(locSet);
        int L = locs.size();
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < L; i++) {
            idx.put(locs.get(i), i);
        }

        // dense P, rebuilt from the SAME maps/expression the weight uses:
        // p_ij = exp(logTransitionCounts(i,j) - allDestination(i)).
        double[][] P = new double[L][L];
        for (Map.Entry<Tuple<String, String>, Double> e : logTransitionCounts.entrySet()) {
            String from = e.getKey().getA();
            String to = e.getKey().getB();
            Double logDen = 0.0;
            if (!rawCountsFlag) {
                logDen = allDestinationLogTransitionCounts.get(from);
            }
            
            if (logDen == null) {
                continue;
            }
            P[idx.get(from)][idx.get(to)] = Math.exp(e.getValue() - logDen);
        }

        double rowSum = 0.0;
        for (int i = 0; i < L; i++) {
            for (int j = 0; j < L; j++) {
                rowSum += P[i][j];
            }
            if (rowSum > 0.0 && Math.abs(rowSum - 1.0) > 1e-3) {
                System.err.println("MovementWeight: row '" + locs.get(i) + "' sums to " + rowSum
                        + " (expected ~1.0); partition normalization assumes a row-stochastic matrix.");
            }
        }

        double logL = Math.log(L);
        double[] logZ = new double[maxStages + 1];
        logZ[0] = 0.0; // empty trip: single empty sequence, weight 1

        double[][] Ppow = eye(L);
        for (int J = 1; J <= maxStages; J++) {
            Ppow = multiply(Ppow, P, L);
            double trace = 0.0;
            for (int i = 0; i < L; i++) {
                trace += Ppow[i][i];
            }
            logZ[J] = (trace > 0.0) ? (Math.log(trace) - J * logL) : Double.NaN;
            // logZ[J] = (trace > 0.0) ? Math.log(trace) : Double.NaN;
        }
        return logZ;
    }

    private static double[][] eye(int L) {
        double[][] I = new double[L][L];
        for (int i = 0; i < L; i++) {
            I[i][i] = 1.0;
        }
        return I;
    }

    private static double[][] multiply(double[][] A, double[][] B, int L) {
        double[][] C = new double[L][L];
        for (int i = 0; i < L; i++) {
            double[] Ai = A[i];
            double[] Ci = C[i];
            for (int k = 0; k < L; k++) {
                double a = Ai[k];
                if (a == 0.0) {
                    continue;
                }
                double[] Bk = B[k];
                for (int j = 0; j < L; j++) {
                    Ci[j] += a * Bk[j];
                }
            }
        }
        return C;
    }

    // -------------------- VALIDATION --------------------
    public void checkKeysAgainstNodes(List<ActivityNode> nodes) {
        for (Tuple<String, String> tr : logTransitionCounts.keySet()) {
            if (!nodes.stream().anyMatch(n -> n.getBasicName().equals(tr.getA()))) {
                throw new IllegalArgumentException("Origin node " + tr.getA() + " not found in scenario");
            }
            if (!nodes.stream().anyMatch(n -> n.getBasicName().equals(tr.getB()))) {
                throw new IllegalArgumentException("Destination node " + tr.getB() + " not found in scenario");
            }
        }
        for (ActivityNode origin : nodes) {
            for (ActivityNode destination : nodes) {
                Tuple<String, String> transition = new Tuple<>(origin.getBasicName(), destination.getBasicName());
                if (!logTransitionCounts.containsKey(transition)) {
                    throw new IllegalArgumentException("Missing transition" + transition);
                }
            }
        }
    }

    // -------------------- SUMMARY WIRING --------------------

    public void configureRequiredSummary(MultiRoundTrip<ActivityNode> mrt) {
        var summary = new MovementSummary(
                logTransitionWeight, epsilon, logZ);
        summary.setGeometricmean(geometricMean);
        summary.setC(c);
        summary.setTraceNormalization(traceNormalized);
        mrt.addSummary(summary);
        mrt.recomputeSummaries();
    }
}