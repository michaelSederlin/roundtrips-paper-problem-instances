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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import se.vti.roundtrips.common.Node;
import se.vti.roundtrips.examples.activityExpandedGridNetwork.Activity;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.multiple.grouping.PopulationGroupFilter;
import se.vti.roundtrips.samplingweights.calibration.TargetDeviationWeight;
import se.vti.roundtrips.simulator.Episode;
import se.vti.roundtrips.simulator.StayEpisode;
import se.vti.roundtrips.single.RoundTrip;
import se.vti.utils.misc.Tuple;

/**
 * Activity-specific modification: Is now configured with the activity type of
 * interest. Goes through all but the first location, checks if it is of the
 * right activity type, takes the longest one as the activities representative
 * location.
 * 
 * @author GunnarF
 *
 */
public class AtActivityLocationTargetWithActivities<N extends Node> extends TargetDeviationWeight<N> {

	private final Activity activity;
	private final Map<String, Double> location2target = new LinkedHashMap<>();
	private final double targetDuration_h;
	private final List<Tuple<Double, Double>> targetIntervals;

	public AtActivityLocationTargetWithActivities(Activity activity, double realPopulationSize, double targetDuration_h,
			double intervalDuration_h, double endTime_h) {
		super(realPopulationSize);
		this.activity = activity;
		this.targetDuration_h = targetDuration_h;
		this.targetIntervals = Collections
				.unmodifiableList(Episode.effectiveIntervals(intervalDuration_h, endTime_h, 24.0));
	}

	public void setTarget(String location, double target) {
		this.location2target.put(location, target);
	}

	@Override
	public String[] createLabels() {
		// solve this with a stream
		String[] result = new String[this.location2target.size()];
		int i = 0;
		for (String taz : this.location2target.keySet()) {
			result[i++] = taz;
		}
		return result;
	}

	@Override
	public double[] computeTarget() {
		return this.location2target.values().stream().mapToDouble(t -> t).toArray();
	}

	@Override
	public double[] computeSample(MultiRoundTrip<N> multiRoundTrip, PopulationGroupFilter<N> filter) {

		Map<String, Integer> loc2cnt = this.location2target.entrySet().stream().collect(
				Collectors.toMap(Map.Entry::getKey, e -> 0, (oldValue, newValue) -> oldValue, LinkedHashMap::new));

		for (RoundTrip<N> roundTrip : (filter == null ? multiRoundTrip : filter.filteredIterable(multiRoundTrip))) {
			double bestOverlap_h = Double.NEGATIVE_INFINITY;
			String bestLocation = null;
			List<?> episodes = roundTrip.getEpisodes();
			for (int i = 2; i < episodes.size(); i += 2) {
				StayEpisode<N> episode = (StayEpisode<N>) episodes.get(i);
				if (this.activity.equals(episode.getLocation().getLabels().get(0))) {
					double overlap_h = episode.overlap_h(this.targetIntervals, 24.0);
					if (overlap_h > bestOverlap_h) {
						bestLocation = episode.getLocation().getBasicName();
						bestOverlap_h = overlap_h;
					}
				}
			}
			if (bestOverlap_h >= this.targetDuration_h) {
				Integer oldCnt = loc2cnt.get(bestLocation);
				if (oldCnt != null /* otherwise not in target */) {
					loc2cnt.put(bestLocation, oldCnt + 1);
				}
			}
		}

		return loc2cnt.values().stream().mapToDouble(c -> c).toArray();
	}
}
