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
    private final double epsilon;
    private final Map<Tuple<String, String>, Double> logTransitionCounts;
    private final Map<String, Double> allDestinationLogTransitionCounts;

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

    public MovementWeight withRawCounts() {
        this.rawCountsFlag = true;
        this.logTransitionWeight = precomputeLogTransitionWeight();
        logger.info("Using raw counts");
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
                ? logNum
                : (logNum - allDestinationLogTransitionCounts.get(e.getKey().getA()));
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
        var summary = new MovementSummary(logTransitionWeight, epsilon);
        mrt.addSummary(summary);
        mrt.recomputeSummaries();
    }
}