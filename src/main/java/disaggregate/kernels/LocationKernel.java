package disaggregate.kernels;

import java.util.ArrayList;
import java.util.List;

import disaggregate.kernels.Features;
import disaggregate.kernels.Kernel;
import disaggregate.kernels.KernelSummary;
import disaggregate.model.Activity;
import disaggregate.model.Person;
import disaggregate.model.nodes.ActivityNode;
import disaggregate.model.nodes.NodeWithActivity;
import se.vti.roundtrips.simulator.Episode;
import se.vti.roundtrips.simulator.StayEpisode;
import se.vti.roundtrips.single.RoundTrip;


/**
 * Base class for location-based kernels.
 * Subclasses should define their own Features inner class implementing the Features interface.
 */
public abstract class LocationKernel<F extends Features> implements Kernel<F> {
    protected final List<Activity> observedActivities;

    /**
     * Base Features implementation for simple location kernels that only need activities.
     */
    public static class LocationFeatures implements Features {
        private final List<Activity> activities;
        
        public LocationFeatures(List<Activity> activities) {
            this.activities = activities;
        }
        
        public List<Activity> getActivities() {
            return activities;
        }
        
        public static <N extends ActivityNode> LocationFeatures from(RoundTrip<N> rt) {
            List<Activity> activities = new ArrayList<>(rt.size());
            for (Episode e : rt.getEpisodes()) {
                if (e instanceof StayEpisode se) {
                    activities.addAll(((ActivityNode) se.getLocation()).getAllActivities());
                }
            }
            return new LocationFeatures(activities);
        }

        public static <N extends ActivityNode> LocationFeatures from(Person p) {
            List<Activity> activities = p.getTrip().getActivities();
            return new LocationFeatures(activities);
        }
    }

    public LocationKernel(List<Activity> observedActivities) {
        this.observedActivities = observedActivities;
    }
    
    public List<Activity> getActivities() {
        return observedActivities;
    }
    
    public abstract F extractFeatures(RoundTrip<NodeWithActivity> rt);
    public abstract F extractFeatures(Person p); 
    
    public abstract double compute(F features);
    
    @Override
    public abstract double compute(RoundTrip<NodeWithActivity> rt, KernelSummary summary);

    @Override
    public double[] getParameters() {
        return new double[0];
    }
    
    @Override
    public void setParameters(double[] params) {
        // Subclasses override if they have parameters
    }

    public void configureRequiredSummary(KernelSummary summary) {
        summary.trackActivities();
    }
}
