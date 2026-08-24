package renate.model.weights;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import renate.model.nodes.ActivityNode;
import renate.model.nodes.NodeWithActivity;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.single.RoundTrip;
import se.vti.utils.misc.metropolishastings.MHWeight;

public class StrictlyEnforceCorrectHomeLocationWeight implements MHWeight<RoundTrip<NodeWithActivity>> {
    private final static Logger logger = LogManager.getLogger(StrictlyEnforceCorrectHomeLocationWeight.class);
    private final List<NodeWithActivity> enforcedHomeNodes;
    private final double forbiddenPenalty = Double.NEGATIVE_INFINITY;
    // private final boolean forceHome = true;

    public StrictlyEnforceCorrectHomeLocationWeight(List<NodeWithActivity> enforcedHomeNodes) {
        this.enforcedHomeNodes = enforcedHomeNodes;
        logger.info("Initialized home location constraint weights for {} roundtrips", enforcedHomeNodes.size());
    }
    
    @Override
    public double logWeight(RoundTrip<NodeWithActivity> rt) {
        // for (int ii = 0; ii < mrt.size(); ii++) {
        //     RoundTrip<NodeWithActivity> rt = mrt.getRoundTrip(ii);

            if (rt.size() == 0) {
                // if (forceHome) {
                    return forbiddenPenalty;
                // } else {
                    // continue;
                // }
            }

            NodeWithActivity firstNode = rt.getNodesView().getFirst();
            ActivityNode enforcedHomeNode = enforcedHomeNodes.get(rt.getIndex());

            if (!firstNode.equals(enforcedHomeNode)) {
                // logger.warn("A roundtrip {} does not start at a valid home location. RT: {}", rt.getIndex(), rt.toString());
                return forbiddenPenalty;
                // throw new IllegalStateException(
                //       "Roundtrip " + rt.getIndex() + " violates home location constraint. RT: " + rt.toString()
                // );
            }
        // }
        // All home node constraints are satisfied!
        return 0.0;
    }


    public boolean isValid(MultiRoundTrip<NodeWithActivity> mrt) {
        boolean testResult = false;
        for (RoundTrip<NodeWithActivity> rt : mrt) {
            testResult = isValid(rt);
        }
        return testResult;
    }
    public boolean isValid(RoundTrip<NodeWithActivity> rt) {
        double lw = logWeight(rt); 
        if (lw == Double.NEGATIVE_INFINITY) {
            throw new IllegalStateException("Invalid home location in multi round trip");
        }
        return true;
    }    
}
