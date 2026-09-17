/**
 * od2roundtrips.model
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

import se.vti.roundtrips.common.Node;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.multiple.grouping.PopulationGroupFilter;
import se.vti.roundtrips.samplingweights.calibration.TargetDeviationWeight;
import se.vti.utils.misc.Tuple;

/**
 * 
 * @author GunnarF
 *
 */
public class ODTargetIgnoringActivities<L extends Node> extends TargetDeviationWeight<L> {

	private final Map<Tuple<String, String>, Double> targetODMatrix = new LinkedHashMap<>();

	public ODTargetIgnoringActivities(double realPopulationSize) {
		super(realPopulationSize);
	}


	public void setODEntry(String origin, String destination, double value) {
		this.targetODMatrix.put(new Tuple<>(origin, destination), value);
	}

	public Map<Tuple<String, String>, Double> getTargetOdMatrix() {
		return this.targetODMatrix;
	}

	private double[] od2array(Map<Tuple<String, String>, ? extends Number> od) {
		double[] result = new double[this.targetODMatrix.size()];
		int i = 0;
		for (Map.Entry<Tuple<String, String>, Double> targetEntry : this.targetODMatrix.entrySet()) {
			if (od.containsKey(targetEntry.getKey())) {
				result[i++] = od.get(targetEntry.getKey()).doubleValue();
			} else {
				result[i++] = 0.0;
			}
		}
		return result;
	}

	@Override
	public double[] computeTarget() {
		return this.od2array(this.targetODMatrix);
	}
	
	@Override
	public double[] computeSample(MultiRoundTrip<L> multiRoundTrip, PopulationGroupFilter<L> filter) {
		
		if (filter != null) {
			throw new RuntimeException("Don't filter, use OD summary per population group instead.");
		}
		return this.od2array(multiRoundTrip.getSummary(ODSummaryIgnoringActivities.class).getODView());
	}
	
	@Override
	public String[] createLabels() {
		String[] result = new String[this.targetODMatrix.size()];
		int i = 0;
		for (Tuple<String, String> od : this.targetODMatrix.keySet()) {
			result[i++] = od.getA() + "->" + od.getB();
		}
		return result;
	}
}
