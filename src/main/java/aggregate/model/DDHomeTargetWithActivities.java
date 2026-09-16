package aggregate.model;

import se.vti.roundtrips.common.Node;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.multiple.grouping.PopulationGroupFilter;

/**
 * @author frupprecht
 */

public class DDHomeTargetWithActivities extends DurationDepartureTarget {

	public DDHomeTargetWithActivities(double realPopulationSize) {
		super(realPopulationSize);
	}

	public double[] computeSample(MultiRoundTrip<Node> multiRoundTrip, PopulationGroupFilter<Node> filter) {
		if (filter != null) {
			throw new RuntimeException("Cannot filter this.");
		}
		return this.dd2Array(multiRoundTrip.getSummary(DDHomeSummaryWithActivities.class).getDDHomeView());
	}
}
