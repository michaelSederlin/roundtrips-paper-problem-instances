package renate.utils.summaries;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import renate.model.Activity;
import renate.model.nodes.NodeWithActivity;
import se.vti.roundtrips.multiple.MultiRoundTripSummary;
import se.vti.roundtrips.simulator.Episode;
import se.vti.roundtrips.simulator.StayEpisode;
import se.vti.roundtrips.single.RoundTrip;

/**
 * For each registered activity, records the single node that "counts" in a round trip:
 * the visited node with the highest opportunity weight sigma. Activities that are not
 * registered are not tracked.
 *
 * Purely derived state -- contributes no log weight.
 */
public class UniqueActivityNodeSummary implements MultiRoundTripSummary<NodeWithActivity> {

    private final Map<Integer, Map<Activity, NodeWithActivity>> rt2activitynodemapping = new HashMap<>();

    /**
     * Ranking key only. Raw sigma and log(sigma + eps) give the same argmax, so either
     * may be passed; likewise the normalisations in LoadOpportunityWeights, which are
     * monotone within an activity.
     */
    private final Map<NodeWithActivity, Double> sigma;

    private final Set<Activity> activities = EnumSet.noneOf(Activity.class);

    public UniqueActivityNodeSummary(Map<NodeWithActivity, Double> sigma) {
        this.sigma = sigma;
    }

    private UniqueActivityNodeSummary(UniqueActivityNodeSummary other) {
        this.rt2activitynodemapping.putAll(other.rt2activitynodemapping);
        this.sigma = other.sigma;
        this.activities.addAll(other.activities);
    }

    public void addActivities(Activity... activities) {
        for (Activity act : activities) {
            this.activities.add(act);
        }
    }

    @Override
    public void clear() {
        rt2activitynodemapping.clear();
    }

    @Override
    public UniqueActivityNodeSummary clone() {
        return new UniqueActivityNodeSummary(this);
    }

    @Override
    public void update(int idx, RoundTrip<NodeWithActivity> oldRoundTrip,
            RoundTrip<NodeWithActivity> newRoundTrip) {

        if (newRoundTrip == null) {
            rt2activitynodemapping.remove(idx);
            return;
        }

        Map<Activity, NodeWithActivity> activityNode = new EnumMap<>(Activity.class);

        for (Episode ep : newRoundTrip.getEpisodes()) {
            if (ep instanceof StayEpisode<?> se) {
                NodeWithActivity node = (NodeWithActivity) se.getLocation();
                Activity nodeAct = node.getActivity();
                if (activities.contains(nodeAct)) {
                    NodeWithActivity curNode = activityNode.get(nodeAct);
                    if ((curNode == null) || (sigmaOf(node) > sigmaOf(curNode))) {
                        activityNode.put(nodeAct, node);
                    }
                }
            }
        }

        rt2activitynodemapping.put(idx, activityNode);
    }

    private double sigmaOf(NodeWithActivity node) {
        Double value = sigma.get(node);
        if (value == null) {
            throw new IllegalStateException("Sigma not found for location: " + node);
        }
        return value;
    }

    /**
     * The node that accrues for this activity, or null if the activity is not registered
     * or is not visited in this round trip.
     */
    public NodeWithActivity getRoundTripActivityNode(int idx, Activity act) {
        Map<Activity, NodeWithActivity> activityNodes = rt2activitynodemapping.get(idx);
        if (activityNodes == null) {
            throw new IllegalStateException(
                "No unique-activity-node data for RoundTrip index " + idx +
                ". Summary has " + rt2activitynodemapping.size() + " entries.");
        }
        return activityNodes.get(act);
    }

    /** All nodes that accrue in this round trip, keyed by activity. Never null. */
    public Map<Activity, NodeWithActivity> getRoundTripActivityNodes(int idx) {
        Map<Activity, NodeWithActivity> activityNodes = rt2activitynodemapping.get(idx);
        if (activityNodes == null) {
            throw new IllegalStateException(
                "No unique-activity-node data for RoundTrip index " + idx +
                ". Summary has " + rt2activitynodemapping.size() + " entries.");
        }
        return activityNodes;
    }
}
