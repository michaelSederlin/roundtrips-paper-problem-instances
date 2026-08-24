package renate.model.weights;

import java.util.Map;

import renate.model.nodes.NodeWithActivity;
import se.vti.roundtrips.simulator.MoveEpisode;
import se.vti.roundtrips.simulator.StayEpisode;
import se.vti.roundtrips.single.RoundTrip;
import se.vti.utils.misc.Tuple;

public class MovementSummary extends LWSummary {
    private static final double FORBIDDEN_LW = -1e12;

    private final Map<Tuple<String, String>, Double> transitionMatrix;
    private final double epsilon;


    public MovementSummary(Map<Tuple<String, String>, Double> transitionMatrix, double epsilon) {
        this.transitionMatrix = transitionMatrix;
        this.epsilon = epsilon;
    }

    public MovementSummary(MovementSummary parent) {
        super(parent);
        this.transitionMatrix = parent.transitionMatrix;
        this.epsilon = parent.epsilon;
    }

    @Override
    protected double roundTrip2LW(RoundTrip<NodeWithActivity> rt) {
        if (rt == null) return 0.0;

        double rawLW = 0.0;
        if (rt.size() == 1) {
            // single self-loop i -> i
            NodeWithActivity loc = ((StayEpisode<NodeWithActivity>) rt.getEpisodes().get(0)).getLocation();
            String locName = loc.getBasicName();
            Tuple<String, String> transition = new Tuple<>(locName, locName);
            rawLW = transitionMatrix.getOrDefault(transition, Math.log(epsilon));
        } else {
            for (int jj = 1; jj < rt.getEpisodes().size(); jj += 2) {
                MoveEpisode<NodeWithActivity> me = (MoveEpisode<NodeWithActivity>) rt.getEpisodes().get(jj);
                Tuple<String, String> transition = new Tuple<>(
                    me.getOrigin().getBasicName(), 
                    me.getDestination().getBasicName()
                );
                if (!transitionMatrix.containsKey(transition)) throw new IllegalArgumentException("Missing transition: " + transition);
                // double logDen = allDestinationLogProbabilities.getOrDefault(transition.getA(), Math.log(epsilon));
                rawLW += transitionMatrix.get(transition);
            }
        }

        return rawLW / rt.size();
    }

    @Override
    public MovementSummary clone() {
        return new MovementSummary(this);
    }
}