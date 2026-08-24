package renate.kernels.activitylocationkernels;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import renate.model.Activity;
import renate.model.Person;
import renate.model.nodes.NodeWithActivity;
import renate.kernels.commons.KernelSummary;
import se.vti.roundtrips.simulator.StayEpisode;
import se.vti.roundtrips.single.RoundTrip;

public class JaccardActivityLocationKernel extends LocationKernel<JaccardActivityLocationKernel.JaccardFeatures> {
    
    public static class JaccardFeatures extends LocationKernel.LocationFeatures {
        public JaccardFeatures(List<Activity> activities) {
            super(activities);
        }
    }

    @Override
    public JaccardFeatures extractFeatures(RoundTrip<NodeWithActivity> rt) {
        return new JaccardFeatures(LocationKernel.LocationFeatures.from(rt).getActivities());
    }

    public JaccardFeatures extractFeatures(Person p) {
        return new JaccardFeatures(LocationKernel.LocationFeatures.from(p).getActivities());
    }
    
    private double lambda;
    private final EnumSet<Activity> obsActivitiesSet;
    private final EnumSet<Activity> simActivitiesTmp = EnumSet.noneOf(Activity.class);

    public JaccardActivityLocationKernel(JaccardFeatures observations) {this(observations.getActivities());}
    public JaccardActivityLocationKernel(Person person) { this(person, 1.0); }
    public JaccardActivityLocationKernel(Person person, double lambda) { this(person.getTrip().getActivities(), lambda); }
    public JaccardActivityLocationKernel(List<Activity> observedActivities) { this(observedActivities, 1.0); }
    public JaccardActivityLocationKernel(List<Activity> observedActivities, double lambda) {
        super(observedActivities);
        if (lambda < 0) throw new IllegalArgumentException("lambda cannot be negative!");
        this.lambda = lambda;
        this.obsActivitiesSet = observedActivities.isEmpty()
                ? EnumSet.noneOf(Activity.class)
                : EnumSet.copyOf(observedActivities);
    }

    private double compute(Set<Activity> simActivities) {
        int intersectionSize = 0;
        for (Activity act : obsActivitiesSet) {
            if (simActivities.contains(act)) {
                intersectionSize++;
            }
        }

        int unionSize = obsActivitiesSet.size() + simActivities.size() - intersectionSize;
        double jaccard = (unionSize == 0) ? 0.0 : (double) intersectionSize / unionSize;
        return Math.exp(-(1.0 - jaccard) / lambda);
    }

    
    @Override
    public double compute(JaccardFeatures features) {
        simActivitiesTmp.clear();
        simActivitiesTmp.addAll(features.getActivities());
        return compute(simActivitiesTmp);
    }

    @Override
    public double compute(RoundTrip<NodeWithActivity> rt, KernelSummary summary) {
        Set<Activity> simulatedActivities = summary.getActivities(rt);
        return compute(simulatedActivities);
    }

    private  double compute(RoundTrip<NodeWithActivity> simulated) {
            JaccardFeatures features = extractFeatures(simulated);
            return compute(features);
    }

    public void setLambda(double val) {
        lambda = val;
    }
    
    @Override
    public double[] getParameters() { return new double[]{lambda}; }

    @Override
    public void setParameters(double[] params) { setLambda(params[0]); }


}
