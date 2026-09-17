/**
 * se.vti.od2roundtrips.targets
 * 
 * Copyright (C) 2024 by Gunnar Flötteröd (VTI, LiU).
 * 
 * VTI = Swedish National Road and Transport Institute
 * LiU = Linköping University, Sweden
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either 
 * version 3 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>. See also COPYING and WARRANTY file.
 */
package aggregate.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import se.vti.roundtrips.common.Node;
import se.vti.roundtrips.examples.activityExpandedGridNetwork.Activity;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.multiple.grouping.PopulationGroupFilter;
import se.vti.roundtrips.samplingweights.calibration.TargetDeviationWeight;
import se.vti.roundtrips.single.RoundTrip;

/**
 * Activity-specific modification: Only considers the first node in a round
 * trip, and only if that node has a HOME label.
 * 
 * @author GunnarF
 *
 */
public class HomeLocationTargetWithActivities<N extends Node> extends TargetDeviationWeight<N> {

	private final Map<String, Double> home2target = new LinkedHashMap<>();

	public HomeLocationTargetWithActivities(double realPopulationSize) {
		super(realPopulationSize);
	}

	public Map<String, Double> getHome2target() {
		return home2target;
	}

	public void setTarget(String node, double target) {
		this.home2target.put(node, target);
	}

	@Override
	public double[] computeTarget() {
		return this.home2target.values().stream().mapToDouble(t -> t).toArray();
	}

	@Override
	public double[] computeSample(MultiRoundTrip<N> multiRoundTrip, PopulationGroupFilter<N> filter) {

		Map<String, Integer> home2sample = this.home2target.entrySet().stream().collect(
				Collectors.toMap(Map.Entry::getKey, e -> 0, (oldValue, newValue) -> oldValue, LinkedHashMap::new));

		for (RoundTrip<N> roundTrip : (filter == null ? multiRoundTrip : filter.filteredIterable(multiRoundTrip))) {
			if ((roundTrip.size() > 0) && Activity.HOME.equals(roundTrip.getNode(0).getLabels().get(0))) {
				home2sample.compute(roundTrip.getNode(0).getBasicName(), (l, c) -> c == null ? 1 : c + 1);
			}
		}
		return home2sample.values().stream().mapToDouble(c -> c).toArray();
	}

	@Override
	public String[] createLabels() {
		String[] result = new String[this.home2target.size()];
		int i = 0;
		for (String taz : this.home2target.keySet()) {
			result[i++] = taz;
		}
		return result;
	}

}
