package aggregate.model;

import java.util.List;


import se.vti.roundtrips.common.Node;
import se.vti.roundtrips.examples.activityExpandedGridNetwork.Activity;
import se.vti.roundtrips.multiple.MultiRoundTripSummary;
import se.vti.roundtrips.simulator.StayEpisode;
import se.vti.roundtrips.single.RoundTrip;

/**
 * Activity-specific modification: Considers the first location in the plan only
 * if it has a HOME label.
 * 
 * @author frupprecht
 */

public class DDHomeSummaryWithActivities extends ViennaSummary {

	// -------------------- CONSTRUCTION --------------------
	public DDHomeSummaryWithActivities() {
		super();
	}

	private DDHomeSummaryWithActivities(DDHomeSummaryWithActivities parent) {
		super(parent);
	}

	// -------------------- GETTERS --------------------
	public double[][] getDDHomeView() {
		return this.ddMatrix;
	}

	// -------------------- IMPLEMENTATION OF ViennaSummary --------------------
	@Override
	public void addToDd(RoundTrip<Node> newRoundTrip) {

		List<?> episodes = newRoundTrip.getEpisodes();
		StayEpisode<Node> firstEpisode = (StayEpisode<Node>) episodes.get(0);

		if (Activity.HOME.equals(firstEpisode.getLocation().getLabels().get(0))) {

			double duration = firstEpisode.getDuration_h();
			double departure = firstEpisode.getEndTime_h();

			// Uses floor for dur_bin to be consistent with the input data.
			// Example: 0 <= dur < 1 --> dur0
			// Clamp 24 -> 23
			int durationBin = (int) Math.floor(duration);
			if (durationBin >= 24) {
				durationBin = 23;
			}

			int departureBin = (int) Math.floor(departure);

			this.ddMatrix[durationBin][departureBin] += 1;
		}
	}

	@Override
	public void removeFromDd(final RoundTrip<Node> oldRoundTrip) {

		List<?> episodes = oldRoundTrip.getEpisodes();
		StayEpisode<Node> homeEpisode = (StayEpisode<Node>) episodes.get(0);

		if (Activity.HOME.equals(homeEpisode.getLocation().getLabels().get(0))) {

			double duration = homeEpisode.getDuration_h();
			double departure = homeEpisode.getEndTime_h();

			// Uses floor for dur_bin to be consistent with the input data.
			// Example: 0 <= dur < 1 --> dur0
			// Clamp 24 -> 23
			int durationBin = (int) Math.floor(duration);
			if (durationBin >= 24) {
				durationBin = 23;
			}

			int departureBin = (int) Math.floor(departure);

			this.ddMatrix[durationBin][departureBin] -= 1;

		}
	}

	// --------------- IMPLEMENTATION OF MultiRoundTripSummaryStats ---------------
	@Override
	public MultiRoundTripSummary<Node> clone() {
		return new DDHomeSummaryWithActivities(this);
	}
}