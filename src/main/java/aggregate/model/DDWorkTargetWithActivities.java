package aggregate.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import se.vti.roundtrips.common.Node;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.multiple.grouping.PopulationGroupFilter;

/**
 * @author frupprecht
 */

public class DDWorkTargetWithActivities extends DurationDepartureTarget {	
	
	// ------------ Fields ------------ 
	private final Set<String> groups;
	
	
	// ------------ Construction ------------ 
	public DDWorkTargetWithActivities(double realPopulationSize, Set<String> groups) {
		super(realPopulationSize);
		this.groups = new LinkedHashSet<>(groups);
	}
	
	// ------------ Construction ------------ 
	public Set<String> getGroupsView() {
		return Collections.unmodifiableSet(this.groups);
	}
	
	public double[] computeSample(MultiRoundTrip<Node> multiRoundTrip, PopulationGroupFilter<Node> filter) {
		if (filter != null) {
			throw new RuntimeException("Cannot filter this.");
		}
		
		// Aggregate summaries over groups
		double[] sample = null;
		for (String group : this.groups) {
			double[] addend = this.dd2Array(multiRoundTrip.getSummary(DDWorkByGroupSummaryWithActivities.class)
					.getSummaries()
					.get(group)
					.getDDWorkView());
			if (sample == null) {
				sample = addend;
			} else {
				assert(sample.length == addend.length);
				for (int i = 0; i < addend.length; i++) {
					sample[i] += addend[i];
				}
			}
		}
		return sample;
	}
}
