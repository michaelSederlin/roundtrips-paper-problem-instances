/**
 * se.vti.roundtrips.od
 * 
 * Copyright (C) 2025 by Gunnar Flötteröd (VTI, LiU).
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
import java.util.Map;

import se.vti.roundtrips.common.Node;
import se.vti.roundtrips.multiple.MultiRoundTripSummary;
import se.vti.roundtrips.single.RoundTrip;
import se.vti.utils.misc.Tuple;

/**
 * 
 * @author GunnarF
 *
 * @param <N>
 */
public class ODSummaryIgnoringActivities<N extends Node> implements MultiRoundTripSummary<N> {

	// -------------------- MEMBERS --------------------

	private final Map<Tuple<String, String>, Integer> odMatrix = new LinkedHashMap<>();

	private int singleTripCnt = 0;

	// -------------------- CONSTRUCTION --------------------

	public ODSummaryIgnoringActivities() {
	}

	// -------------------- GETTERS --------------------

	public Map<Tuple<String, String>, Integer> getODView() {
		return Collections.unmodifiableMap(this.odMatrix);
	}

	public int getSingleTripCnt() {
		return this.singleTripCnt;
	}

	// --------------- IMPLEMENTATION OF MultiROundTripSummaryStats ---------------

	@Override
	public void clear() {
		this.odMatrix.clear();
		this.singleTripCnt = 0;
	}
	
	@Override
	public void update(int i, RoundTrip<N> oldRoundTrip, RoundTrip<N> newRoundTrip) {
		
		if ((newRoundTrip != null) && (newRoundTrip.size() > 1)) {
			this.singleTripCnt += newRoundTrip.size();
			for (int l = 0; l < newRoundTrip.size(); l++) {
				Tuple<String, String> od = new Tuple<>(newRoundTrip.getNode(l).getBasicName(), newRoundTrip.getSuccessorNode(l).getBasicName());
				this.odMatrix.compute(od, (od2, v) -> v == null ? 1 : v + 1);
			}
		}
		
		if ((oldRoundTrip != null) && (oldRoundTrip.size() > 1)) {
			this.singleTripCnt -= oldRoundTrip.size();
			for (int l = 0; l < oldRoundTrip.size(); l++) {
				Tuple<String, String> od = new Tuple<>(oldRoundTrip.getNode(l).getBasicName(), oldRoundTrip.getSuccessorNode(l).getBasicName());
				int oldVal = this.odMatrix.get(od);
				if (oldVal > 1) {
					this.odMatrix.put(od, oldVal - 1);
				} else {
					this.odMatrix.remove(od);
				}
			}
		}
	}

	@Override
	public MultiRoundTripSummary<N> clone() {
		final ODSummaryIgnoringActivities<N> result = new ODSummaryIgnoringActivities<N>();
		result.odMatrix.putAll(this.odMatrix);
		result.singleTripCnt = this.singleTripCnt;
		return result;
	}

}
