package common;

import disaggregate.model.Activity;
import disaggregate.model.ActivityNode;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.simulator.Episode;
import se.vti.roundtrips.simulator.StayEpisode;
import se.vti.utils.misc.metropolishastings.MHToFileLogger;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/*
Author: Michael Sederlin
2025 

Made to log the simulated activity duration over iterations

 */

public class ActivityDurationLogger<N extends ActivityNode> extends MHToFileLogger<MultiRoundTrip<N>> {
    public ActivityDurationLogger(long samplingInterval, Path logFileName) { this(samplingInterval, logFileName.toString()); }
    public ActivityDurationLogger(long samplingInterval, String logFileName) {
        super(samplingInterval, logFileName);
    }

    @Override
    public String createHeaderLine() {
        return "iteration\tidx\tactivity\tduration";
    }

    @Override
    public String createDataLine(MultiRoundTrip<N> mrt, double lw) {
        StringBuilder sb = new StringBuilder();
        for (var rt : mrt) {
            Map<Activity, Double> act2totalDur_h = new EnumMap<>(Activity.class);
            List<Episode> episodes = rt.getEpisodes();
            for (int ii = 0; ii < episodes.size(); ii += 2) {
                var stay = (StayEpisode<N>) episodes.get(ii);
                act2totalDur_h.merge(
                        stay.getLocation().getPrimaryActivity(),
                        stay.getDuration_h(),
                        Double::sum
                );
            }

            for (var e : act2totalDur_h.entrySet()) {
                sb.append(this.iteration()).append("\t")
                    .append(rt.getIndex()).append("\t")
                    .append(e.getKey().name()).append("\t")
                    .append(e.getValue()).append("\n");
            }
        }
        return sb.toString();
    }
}
