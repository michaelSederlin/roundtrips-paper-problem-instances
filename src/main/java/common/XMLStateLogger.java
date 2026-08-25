package common;

import java.nio.file.Path;

import se.vti.roundtrips.common.Node;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.simulator.Episode;
import se.vti.roundtrips.simulator.MoveEpisode;
import se.vti.roundtrips.simulator.StayEpisode;
import se.vti.roundtrips.single.RoundTrip;

public class XMLStateLogger<N extends Node> extends XMLFileLogger<MultiRoundTrip<N>> {
	public XMLStateLogger(long samplingInterval, String logPath) {
		this(samplingInterval, Path.of(logPath));
	}

	public XMLStateLogger(long samplingInterval, Path logPath) {
		super(samplingInterval, (logPath.toFile().isDirectory() ? 
		                        logPath.resolve("states.xml").toString() : 
		                        logPath.toString()), "MultiRoundTrips", "UTF-8");
	}

	@Override
	public String createDataLine(MultiRoundTrip<N> mr, double lw) {

		// final int[] departures = new int[scenario.getTimeBinCnt()];
		// for (RoundTrip<?> r : state) {
		// 	if (r.size() > 1) {
		// 		for (int i = 0; i < r.size(); i++) {
		// 			departures[r.getDeparture(i)]++;
		// 		}
		// 	}
		// }

		StringBuilder sb = new StringBuilder();
		sb.append("<MultiRoundTrip iteration=\"").append(this.iteration()).append("\">\n");
		for (RoundTrip r : mr) {
			sb.append("\t<RoundTrip index=\"").append(r.getIndex()).append("\">\n");
			sb.append("\t\t<Nodes>").append(r.getNodesView().toString()).append("</Nodes>\n");
			sb.append("\t\t<Departures>").append(r.getDeparturesView().toString()).append("</Departures>\n");
			sb.append("\t\t<Episodes>\n");
			for (Object obj : r.getEpisodes()) {
				Episode e = (Episode) obj;
				sb.append("\t\t\t<").append(e.getClass().getSimpleName())
				.append(" start=\"").append(e.getEndTime_h() - e.getDuration_h()).append("\"")
				.append(" end=\"").append(e.getEndTime_h()).append("\"");
				if (e instanceof StayEpisode) {
					sb.append(" loc=\"").append(((StayEpisode<N>) e).getLocation().getName()).append("\"")
					  .append(" labels=\"").append(((StayEpisode<N>) e).getLocation().getLabels().toString()).append("\"");
				} else if (e instanceof MoveEpisode) {
					sb.append(" from=\"").append(((MoveEpisode<N>) e).getOrigin().getBasicName()).append("\"")
					  .append(" to=\"").append(((MoveEpisode<N>) e).getDestination().getBasicName()).append("\"")
					  .append(" duration=\"").append(((MoveEpisode<N>) e).getDuration_h()).append("\"");
				}
            sb.append("/>\n");
            }
			sb.append("\t\t</Episodes>\n");
			sb.append("\t</RoundTrip>\n");
        }
        sb.append("</MultiRoundTrip>");
			return sb.toString();
	}
}