package disaggregate.weights;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import disaggregate.model.Person;
import disaggregate.utils.Trip;
import common.Activity;
import common.GridNodeWithActivity;
import se.vti.roundtrips.simulator.IntervalUtils;
import se.vti.roundtrips.simulator.StayEpisode;
import se.vti.roundtrips.single.RoundTrip;
import se.vti.utils.misc.Tuple;

/**
 * Extracts, per activity, the time intervals during which that activity is
 * performed -- either from an observed {@link Person}'s trip or from a
 * simulated {@link RoundTrip}.
 */
public class ActivityIntervals {

    private ActivityIntervals() {
    }

    private static Map<Activity, List<Tuple<Double, Double>>> createEmptyActivityIntervals() {
        Map<Activity, List<Tuple<Double, Double>>> map = new HashMap<>();
        for (Activity act : Activity.values()) {
            map.put(act, new ArrayList<>());
        }
        return map;
    }

    
    public static Map<Activity, List<Tuple<Double, Double>>> from(Person p) {
        return from(p, 24.0);
    }

    public static Map<Activity, List<Tuple<Double, Double>>> from(Person p, double horizon_h) {
        Trip trip = p.getTrip();
        List<Trip.Leg> legs = trip.getLegs();
        Map<Activity, List<Tuple<Double, Double>>> activityMap = createEmptyActivityIntervals();

        // Add initial HOME episode: [0, first departure]. This assumes first activity is a home activity in the observations and needs to be generalized / handled differently if this is not the case. 
        if (!legs.isEmpty()) {
            double firstDep_h = legs.get(0).start / 60.0;
            firstDep_h = Math.max(0.0, Math.min(horizon_h, firstDep_h));
            addInterval(activityMap, Activity.HOME, 0.0, firstDep_h);
        }

        for (int ii = 0; ii < legs.size(); ii++) {
            Trip.Leg leg = legs.get(ii);
            Activity activity = leg.destinationActivity;

            double start_h = leg.end / 60.0;
            double end_h = (ii < legs.size() - 1) ? (legs.get(ii + 1).start / 60.0) : horizon_h;

            start_h = Math.max(0.0, Math.min(horizon_h, start_h));
            end_h   = Math.max(0.0, Math.min(horizon_h, end_h));

            addInterval(activityMap, activity, start_h, end_h);
        }

        return activityMap;
    }

    public static Map<Activity, List<Tuple<Double, Double>>> from(RoundTrip<? extends GridNodeWithActivity> rt) {
        return from(rt, 24.0, Map.of());
    }

    /**
     * @param accruingNodes for activities restricted to a single node, the node that counts.
     *                      An activity absent from the map is not restricted and all of its
     *                      stays are included.
     *
     * this aims to ensure that activities are not distributed across nodes excessively
     */

    public static Map<Activity, List<Tuple<Double, Double>>> from(RoundTrip<? extends GridNodeWithActivity> rt, double horizon_h, Map<Activity, GridNodeWithActivity> accruingNodes) {
        Map<Activity, List<Tuple<Double, Double>>> activityMap = createEmptyActivityIntervals();

        var intervalUtils = new IntervalUtils(24.0);

        for (Activity act : Activity.values()) {
            GridNodeWithActivity accruingNode = accruingNodes.get(act);
            List<Tuple<Double, Double>> intervals = intervalUtils.extractIntervals(
                rt.getEpisodes(),
                ep -> (ep instanceof StayEpisode<?> se)
                && (
                    ((GridNodeWithActivity) se.getLocation()).getActivity().equals(act)
                    && ((accruingNode == null) || (accruingNode.equals(se.getLocation()))))
                );
            activityMap.put(act, intervals);
        }

        return activityMap;
    }

    private static void addInterval(Map<Activity, List<Tuple<Double, Double>>> intervalsMap,
                                    Activity activity, double start_h, double end_h) {
        if (!(end_h > start_h)) return;
        intervalsMap.computeIfAbsent(activity, k -> new ArrayList<>())
                    .add(new Tuple<>(start_h, end_h));
    }
}
