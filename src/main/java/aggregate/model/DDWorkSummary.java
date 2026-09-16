package aggregate.model;

import java.util.List;

import se.vti.roundtrips.common.Node;
import se.vti.roundtrips.multiple.MultiRoundTripSummary;
import se.vti.roundtrips.simulator.StayEpisode;
import se.vti.roundtrips.single.RoundTrip;

/**
 * @author frupprecht
 */

public class DDWorkSummary extends ViennaSummary {

    // -------------------- CONSTRUCTION --------------------
    public DDWorkSummary() {
        super();
    }

    private DDWorkSummary(DDWorkSummary parent) {
        super(parent);
    }

    // -------------------- GETTERS --------------------
    public double[][] getDDWorkView() {
        return this.ddMatrix;
    }

    // -------------------- IMPLEMENTATION OF ViennaSummary --------------------
    @Override
    public void addToDd(RoundTrip<Node> newRoundTrip) {
        List<?> episodes = newRoundTrip.getEpisodes();

        double longestDuration = -1.0;
        double departureAtLongest = 0.0;

        for (int i = 2; i < episodes.size(); i += 2) {
            StayEpisode<Node> episode = (StayEpisode<Node>) episodes.get(i);

            double duration = episode.getDuration_h();
            double departure = episode.getEndTime_h();

            if (duration > longestDuration) {
                longestDuration = duration;
                departureAtLongest = departure;
            }
        }

        int durationBin = (int) Math.floor(longestDuration);
        if (durationBin >= 24) {
            durationBin = 23;
        }

        int departureBin = (int) Math.floor(departureAtLongest);
        this.ddMatrix[durationBin][departureBin] += 1;
    }

    
    @Override
    public void removeFromDd(final RoundTrip<Node> oldRoundTrip) {
        List<?> episodes = oldRoundTrip.getEpisodes();

        double longestDuration = -1.0;
        double departureAtLongest = 0.0;

        for (int i = 2; i < episodes.size(); i += 2) {
            StayEpisode<Node> episode = (StayEpisode<Node>) episodes.get(i);

            double duration = episode.getDuration_h();
            double departure = episode.getEndTime_h();

            if (duration > longestDuration) {
                longestDuration = duration;
                departureAtLongest = departure;
            }
        }

        int durationBin = (int) Math.floor(longestDuration);
        if (durationBin >= 24) {
            durationBin = 23;
        }

        // Dep_bin like in DDHomeSummary
        int departureBin = (int) Math.floor(departureAtLongest);

        this.ddMatrix[durationBin][departureBin] -= 1;
    }

    // --------------- IMPLEMENTATION OF MultiRoundTripSummaryStats ---------------
    @Override
    public MultiRoundTripSummary<Node> clone() {
        return new DDWorkSummary(this);
    }
}