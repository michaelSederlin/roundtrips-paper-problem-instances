package common.logging;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import common.Activity;
import common.GridNodeWithActivity;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.simulator.Episode;
import se.vti.roundtrips.simulator.StayEpisode;
import se.vti.roundtrips.single.RoundTrip;

/**
 * Logs the duration and departure time of the longest episode 
 * for each activity type within a round-trip.
 */

public class ActivityDepartureDurationLogger extends XMLFileLogger<MultiRoundTrip<GridNodeWithActivity>>{



	public ActivityDepartureDurationLogger(long samplingInterval, String logFileName) {
		super(samplingInterval, logFileName, "ActivityDurations", "UTF-8");
	}

	@Override
	public String createDataLine(MultiRoundTrip<GridNodeWithActivity> mrt, double lw) {
		StringBuilder sb = new StringBuilder();
		sb.append("<Roundtrips iteration=\"" + this.iteration() + "\">\n");
		for (RoundTrip<GridNodeWithActivity> rt : mrt) {
			List<Episode> episodes = rt.getEpisodes();
			Map<Activity, Double> activityDurations = new HashMap<>();
			Map<Activity, Double> departureTimes = new HashMap<>();

			for (int ii = 0; ii < episodes.size(); ii += 2) {
				var stay = (StayEpisode<GridNodeWithActivity>) episodes.get(ii);
				Activity activity = stay.getLocation().getActivity();

				// Get duration of the current episode
				double currentStayDur = stay.getDuration_h();
				
				// Get previous maximum duration (default 0.0)
				double existingMaxDur = activityDurations.getOrDefault(activity, 0.0);

				// Update only if the current episode is longer than the stored maximum
				if (currentStayDur > existingMaxDur) {
					// Store the longest duration
					activityDurations.put(activity, currentStayDur);

					// Store the matching departure time of this longest episode
					double depTime = stay.getEndTime_h();
					departureTimes.put(activity, depTime);
				}
			}

			sb.append("\t<Roundtrip index=\"" + rt.getIndex() + "\"");
			for (Activity act : Activity.values()) {
				double dur = activityDurations.getOrDefault(act, 0.0);
				double dep = departureTimes.getOrDefault(act, 0.0);

				// Write duration and departure to XML
				sb.append(" " + act.toString() + "_duration=\"" + dur + "\"");
				sb.append(" " + act.toString() + "_departure=\"" + dep + "\"");
			}
			sb.append("/>\n");
		}
		sb.append("</Roundtrips>\n");

		return sb.toString();
	}

}