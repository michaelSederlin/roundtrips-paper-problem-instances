package disaggregate.weights;

import disaggregate.model.Activity;
import disaggregate.model.ActivityNode;
import se.vti.roundtrips.single.RoundTrip;
import se.vti.utils.misc.metropolishastings.MHWeight;

/**
 * 
 * @author GunnarF
 * Modified by Michael
 */
public class StrictlyEnforceUniqueHomeLocation implements MHWeight<RoundTrip<ActivityNode>> {
	private double forbidden = Double.NEGATIVE_INFINITY;
	private double penalized = -10000.0;

	@Override
	public double logWeight(RoundTrip<ActivityNode> roundTrip) {
		if (roundTrip.size() == 0) {
			return forbidden;
		}
		ActivityNode firstNode = roundTrip.getNode(0);
		if (Activity.HOME.equals(firstNode.getActivity())) {
			for (int i = 1; i < roundTrip.size(); i++) {
				ActivityNode node = roundTrip.getNode(i);
				if (Activity.HOME.equals(node.getActivity()) && !(firstNode.equals(node))) {
					return forbidden;
				}
			}
			return 0;
		} else {
			return forbidden;
		}
	}
}
