package renate.kernels.schedulekernels;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import renate.kernels.commons.Kernel;
import renate.kernels.commons.KernelSummary;
import renate.kernels.commons.ScheduleOverlapFeatures;
import renate.model.Activity;
import renate.model.Person;
import renate.model.nodes.NodeWithActivity;
import se.vti.roundtrips.simulator.IntervalUtils;
import se.vti.roundtrips.single.RoundTrip;
import se.vti.utils.misc.Tuple;

public class GaussianScheduleOverlapKernel implements Kernel<ScheduleOverlapFeatures>, ScheduleKernelBase {
    private double MIN_LAM = 0.1;
    private Map<Activity, Double> lambda2PerActivity = new EnumMap<>(Activity.class);
    private double lambda = 1.0;
    private double lambda2 = 1.0;
    private final Map<Activity, List<Tuple<Double, Double>>> observedActivityIntervals;
    private final IntervalUtils intervalUtils;

    public GaussianScheduleOverlapKernel(Map<Activity, List<Tuple<Double, Double>>> observedActivityIntervals) {
        this.observedActivityIntervals = observedActivityIntervals;
        this.intervalUtils = new IntervalUtils(24.0);
    }

    public GaussianScheduleOverlapKernel(Person person) {
        this.observedActivityIntervals = ScheduleOverlapFeatures.from(person).getActivityIntervals();
        this.intervalUtils = new IntervalUtils(24.0);
    }

    public GaussianScheduleOverlapKernel(Person person, Double c, Map<Activity, Double> medianDurations) {
        this(person);
        for (Map.Entry<Activity, Double> entry : medianDurations.entrySet()) {
            Double median = entry.getValue();
            if (median == null || median <= 0) {
                throw new IllegalArgumentException("Invalid median duration for activity " + entry.getKey() + ": " + median);
            }
            double lam = Math.max(MIN_LAM, c * median);
            this.lambda2PerActivity.put(entry.getKey(), lam * lam);
        }
        // The provided medians define the active activity set; an observed activity outside it
        // would silently go unscored, so treat it as a configuration error.
        for (Map.Entry<Activity, List<Tuple<Double, Double>>> obs : observedActivityIntervals.entrySet()) {
            if (obs.getKey() != Activity.HOME && !obs.getValue().isEmpty()
                    && !this.lambda2PerActivity.containsKey(obs.getKey())) {
                throw new IllegalArgumentException("Observed activity " + obs.getKey()
                        + " has no --median-duration entry (person " + person.getName() + ")");
            }
        }
    }

    public GaussianScheduleOverlapKernel(Person person, double lambda) {
        this(person);
        this.lambda = lambda;
        this.lambda2 = lambda * lambda;
    }
    
    @Override
    public double compute(RoundTrip<NodeWithActivity> rt, KernelSummary summary) {

        Double cached = getCached(rt, summary);
        if (cached != null) return cached;

        Map<Activity, List<Tuple<Double, Double>>> simActivityIntervals = summary.getActivityIntervals(rt);

        double acc = 0.0;

        // Per-activity bandwidths (when given) define the active activity set; otherwise
        // score all activities with the global bandwidth.
        Iterable<Activity> activeActivities = lambda2PerActivity.isEmpty()
                ? List.of(Activity.values())
                : lambda2PerActivity.keySet();

        for (Activity act : activeActivities) {

            // if (act.equals(Activity.HOME)) {
            //     continue;
            // }
            
            List<Tuple<Double, Double>> sim = simActivityIntervals.get(act);
            List<Tuple<Double, Double>> obs = observedActivityIntervals.get(act);

            double lenSim = 0.0;
            if (sim != null) {
                for (var t : sim ) lenSim += t.getB() - t.getA();
            }

            double lenObs = 0.0;
            if (obs != null) {
                for (var t : obs ) lenObs = lenObs + (t.getB() - t.getA());
            }

            double inter = 0.0;
            if (sim != null && obs != null) {
                inter = this.intervalUtils.computeOverlap_h(sim, obs);
            }

            double d = Math.max(0.0, lenSim + lenObs - 2 * inter); // union - inter = (lenSim + lenObs - inter) - (inter);
            acc += (d * d) / lambda2PerActivity.getOrDefault(act, lambda2);
        }

        double k = Math.exp(-0.5 * acc);

        setCached(rt, summary, k);
        return k;
    }

    @Override
    public double compute(ScheduleOverlapFeatures val) {
        throw new UnsupportedOperationException("Unimplemented method 'compute'");
    }

    @Override
    public double[] getParameters() {
        return new double[]{this.lambda};
    }

    @Override
    public void setParameters(double[] p) {
        this.lambda = p[0];
        this.lambda2 = this.lambda * this.lambda;
    }

    @Override
    public ScheduleOverlapFeatures extractFeatures(RoundTrip<NodeWithActivity> rt) {
        return ScheduleOverlapFeatures.from(rt);
    }

    @Override
    public ScheduleOverlapFeatures extractFeatures(Person p) {
        return ScheduleOverlapFeatures.from(p);
    }

    @Override
    public void configureRequiredSummary(KernelSummary summary) {
        summary.setTrackActivityIntervals(true);
    }
}
