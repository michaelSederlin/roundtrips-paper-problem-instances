package disaggregate.weights;

import common.Activity;
import common.GridNodeWithActivity;
import se.vti.roundtrips.single.RoundTrip;
import se.vti.utils.misc.metropolishastings.MHWeight;

/**
 * 
 * @author GunnarF
 * Modified by Michael
 */
public class StrictlyEnforceUniqueHomeLocation implements MHWeight<RoundTrip<GridNodeWithActivity>> {
	private double forbidden = Double.NEGATIVE_INFINITY;
	private double penalized = -10000.0;

	@Override
	public double logWeight(RoundTrip<GridNodeWithActivity> roundTrip) {
		if (roundTrip.size() == 0) {
			return forbidden;
		}
		GridNodeWithActivity firstNode = roundTrip.getNode(0);
		if (Activity.HOME.equals(firstNode.getActivity())) {
			for (int i = 1; i < roundTrip.size(); i++) {
				GridNodeWithActivity node = roundTrip.getNode(i);
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
