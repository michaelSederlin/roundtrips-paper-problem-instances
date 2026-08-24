package disaggregate.model.weights;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import disaggregate.model.ActivityNode;
import disaggregate.model.ActivityNode;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.single.RoundTrip;
import se.vti.utils.misc.metropolishastings.MHWeight;

public class StrictlyEnforceCorrectHomeLocationWeight implements MHWeight<RoundTrip<ActivityNode>> {
    private final static Logger logger = LogManager.getLogger(StrictlyEnforceCorrectHomeLocationWeight.class);
    private final List<ActivityNode> enforcedHomeNodes;
    private final double forbiddenPenalty = Double.NEGATIVE_INFINITY;
    // private final boolean forceHome = true;

    public StrictlyEnforceCorrectHomeLocationWeight(List<ActivityNode> enforcedHomeNodes) {
        this.enforcedHomeNodes = enforcedHomeNodes;
        logger.info("Initialized home location constraint weights for {} roundtrips", enforcedHomeNodes.size());
    }
    
    @Override
    public double logWeight(RoundTrip<ActivityNode> rt) {
        // for (int ii = 0; ii < mrt.size(); ii++) {
        //     RoundTrip<ActivityNode> rt = mrt.getRoundTrip(ii);

            if (rt.size() == 0) {
                // if (forceHome) {
                    return forbiddenPenalty;
                // } else {
                    // continue;
                // }
            }

            ActivityNode firstNode = rt.getNodesView().getFirst();
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


    public boolean isValid(MultiRoundTrip<ActivityNode> mrt) {
        boolean testResult = false;
        for (RoundTrip<ActivityNode> rt : mrt) {
            testResult = isValid(rt);
        }
        return testResult;
    }
    public boolean isValid(RoundTrip<ActivityNode> rt) {
        double lw = logWeight(rt); 
        if (lw == Double.NEGATIVE_INFINITY) {
            throw new IllegalStateException("Invalid home location in multi round trip");
        }
        return true;
    }    
}
