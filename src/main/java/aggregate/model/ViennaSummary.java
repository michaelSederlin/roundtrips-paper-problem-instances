/**
 * instances.vienna
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

import java.util.Arrays;
import se.vti.roundtrips.common.Node;
import se.vti.roundtrips.multiple.MultiRoundTripSummary;
import se.vti.roundtrips.single.RoundTrip;

/**
 * 
 * @author GunnarF
 *
 */

public abstract class ViennaSummary implements MultiRoundTripSummary<Node> {

	final int timeBins = 24;

	public final double[][] ddMatrix = new double[this.timeBins][this.timeBins];

	public ViennaSummary() {		
	}
	
	public ViennaSummary(ViennaSummary parent) {
		for (int row = 0; row < this.ddMatrix.length; row++) {
			System.arraycopy(parent.ddMatrix[row], 0, this.ddMatrix[row], 0, this.timeBins);
		}
	}

	// -------------------- INTERFACE DEFINITION --------------------

	public abstract void addToDd(RoundTrip<Node> newRoundTrip);

	public abstract void removeFromDd(final RoundTrip<Node> oldRoundTrip);

	// ---------- PARTIAL IMPLEMENTATION OF MultiRoundTripSummaryStats ----------

	@Override
	public void clear() {
		for (int row = 0; row < this.ddMatrix.length; row++) {
			Arrays.fill(this.ddMatrix[row], 0.0);
		}
	}

	@Override
	public void update(int i, RoundTrip<Node> oldRoundTrip, RoundTrip<Node> newRoundTrip) {
		if ((newRoundTrip != null) && (newRoundTrip.size() > 1)) {
			this.addToDd(newRoundTrip);
		}
		if ((oldRoundTrip != null) && (oldRoundTrip.size() > 1)) {
			this.removeFromDd(oldRoundTrip);
		}
	}

	@Override
	abstract public MultiRoundTripSummary<Node> clone();

}
