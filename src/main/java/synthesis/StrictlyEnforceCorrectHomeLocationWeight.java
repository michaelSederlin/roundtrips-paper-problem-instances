package synthesis;

import java.util.List;
import java.util.Set;

import se.vti.roundtrips.common.Node;
import se.vti.roundtrips.single.RoundTrip;
import se.vti.utils.misc.metropolishastings.MHWeight;

public class StrictlyEnforceCorrectHomeLocationWeight<N extends Node> implements MHWeight<RoundTrip<N>> {
    private final List<Set<N>> enforcedHomeNodesSets;
    private final double forbiddenState = Double.NEGATIVE_INFINITY;

    public StrictlyEnforceCorrectHomeLocationWeight(List<Set<N>> enforcedHomeNodesSets) {
        this.enforcedHomeNodesSets = enforcedHomeNodesSets;
    }

    @Override
    public double logWeight(RoundTrip<N> rt) {
        if (rt.size() == 0) {
            return forbiddenState;
        }
        N firstNode = rt.getNodesView().get(0);
        Set<N> enforcedHomeNodeSet = enforcedHomeNodesSets.get(rt.getIndex());

        if (!enforcedHomeNodeSet.contains(firstNode)) {
            return forbiddenState;
        }
        return 0.0;
    }
//    public void isValid(MultiRoundTrip<N> mrt) {
//        double lw = logWeight(mrt);
//        if (lw == Double.NEGATIVE_INFINITY) {
//            throw new IllegalStateException("Invalid home location in multi round trip");
//        }
//    }
}
