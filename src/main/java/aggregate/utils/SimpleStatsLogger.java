/**
 * od2roundtrips.model
 * * Copyright (C) 2024 by Gunnar Flötteröd (VTI, LiU).
 * * VTI = Swedish National Road and Transport Institute
 * LiU = Linköping University, Sweden
 * * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either 
 * version 3 of the License, or (at your option) any later version.
 * * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>. See also COPYING and WARRANTY file.
 */
package aggregate.utils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import se.vti.roundtrips.common.Node;
import se.vti.roundtrips.common.Scenario;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.single.RoundTrip;
import se.vti.utils.misc.metropolishastings.MHStateProcessor;

/**
 * * @author GunnarF
 *
 */
public class SimpleStatsLogger<L extends Node> implements MHStateProcessor<MultiRoundTrip<L>> {

	// -------------------- Fields --------------------
	
//	private String errorsFile = "./output/errors.log";
	private String locationsFile;
	private String departuresFile;

	private final Scenario<L> scenario;

	private final long interval;

	private long iteration;
	


	// -------------------- Constructor --------------------
	
	public SimpleStatsLogger(Scenario<L> scenario, long interval, String outputFolder) {
		this.scenario = scenario;
		this.interval = interval;
		this.locationsFile = outputFolder + "locationCounts.log";
		this.departuresFile = outputFolder + "departures.log";
	}
	

	// -------------------- Functionality --------------------
	
	private void append(String line, String fileName) {
		try {
			FileUtils.writeStringToFile(new File(fileName), line + "\n", true);
		} catch (IOException e1) {
			throw new RuntimeException(e1);
		}
	}

	@Override
	public void start() {
		this.iteration = 0;
//		new File(this.errorsFile).delete();
		new File(this.locationsFile).delete();
		new File(this.departuresFile).delete();
	}

	@Override
	public void processState(MultiRoundTrip<L> state) {

//		final MultiRoundTripWithOD<?> stateWithOD = (MultiRoundTripWithOD<?>) state;
		
		if (this.iteration++ % this.interval == 0) {

			//
//			this.append("" + odSummary.getInfoReproductionError(), this.errorsFile);

			//
//			final int[] locations = new int[scenario.getMaxPossibleStayEpisodes() + 1];	removed in v0.1.15
			final int[] locations = new int[scenario.getNumberOfTimeBins() + 1];
			for (RoundTrip<?> r : state) {
				locations[r.size()]++;
			}
			this.append(Arrays.stream(locations).mapToObj(l -> "" + l).collect(Collectors.joining("\t")),
					this.locationsFile);

			//
			final int[] departures = new int[scenario.getTimeBinCnt()];
			for (RoundTrip<?> r : state) {
				if (r.size() > 1) {
					for (int i = 0; i < r.size(); i++) {
						departures[r.getDeparture(i)]++;
					}
				}
			}
			this.append(Arrays.stream(departures).mapToObj(l -> "" + l).collect(Collectors.joining("\t")),
					this.departuresFile);


		}
	}

	@Override
	public void end() {
	}

}