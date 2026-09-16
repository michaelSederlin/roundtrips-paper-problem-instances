package aggregate.utils;

import java.nio.file.Path;

import se.vti.roundtrips.common.Node;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.utils.misc.metropolishastings.MHWeightContainer;

import se.vti.roundtrips.simulator.Episode;
import se.vti.roundtrips.simulator.MoveEpisode;
import se.vti.roundtrips.simulator.StayEpisode;
import se.vti.roundtrips.single.RoundTrip;


public class XMLStateLogger<N extends Node> extends XMLFileLogger<MultiRoundTrip<N>> {

	
	
	// ------------ Fields -------------

	private MHWeightContainer<MultiRoundTrip<N>> samplingWeights;
	private long firstIteration; 


	
	// ------------ Constructor -------------

	public XMLStateLogger(long samplingInterval, String logFileName) {
		super(samplingInterval, logFileName, "MultiRoundTrips", "UTF-8");
	}
	
	public XMLStateLogger(long samplingInterval, String logFileName, MHWeightContainer<MultiRoundTrip<N>> weights) {
		this(samplingInterval, logFileName);
		this.samplingWeights = weights;
	}

	public XMLStateLogger(long samplingInterval, String logFileName, long firstIteration) {
		this(samplingInterval, logFileName);
		this.firstIteration = firstIteration;
	}



	
	
	// ------------ Functionality -------------

	@Override
	public String createDataLine(MultiRoundTrip<N> mr, double lw) {

		if (this.iteration() < this.firstIteration) {
			return null;
		}

		StringBuilder sb = new StringBuilder();
		sb.append("<MultiRoundTrip iteration=\"").append(this.iteration()).append("\">\n");
		sb.append("\t<SamplingWeights>\n");
		if (this.samplingWeights != null) {
			for (var samplingWeight : this.samplingWeights.getComponentsView()) {
				sb.append("\t\t<Weight name=\"").append(samplingWeight.name()).append("\">")
				.append(samplingWeight.logWeight(mr))
				.append("</Weight>\n");
			}
		}
		sb.append("\t</SamplingWeights>\n");
		for (RoundTrip r : mr) {
			sb.append("\t<RoundTrip index=\"").append(r.getIndex()).append("\">\n");
			sb.append("\t\t<State>\n");
			sb.append("\t\t\t").append(r.toString()).append("\n");
			sb.append("\t\t</State>\n");
			sb.append("\t\t<Episodes>\n");
			for (Object obj : r.getEpisodes()) {
				Episode e = (Episode) obj;
				sb.append("\t\t\t<").append(e.getClass().getSimpleName())
				.append(" start=\"").append(e.getEndTime_h() - e.getDuration_h()).append("\"")
				.append(" end=\"").append(e.getEndTime_h()).append("\"");
				if (e instanceof StayEpisode) {
					N n = (N) ((StayEpisode) e).getLocation();

					sb.append(" loc=\"").append(n.getName()).append("\"");
//					.append(" activity=\"").append(n.getActivity().toString()).append("\"");
				} else if (e instanceof MoveEpisode) {
					sb.append(" from=\"").append(((MoveEpisode) e).getOrigin().getName()).append("\"")
					.append(" to=\"").append(((MoveEpisode) e).getDestination().getName()).append("\"")
					.append(" duration=\"").append(((MoveEpisode) e).getDuration_h()).append("\"");					
				}
				sb.append("/>\n");
			}
			sb.append("\t\t</Episodes>\n");

//			 sb.append("\t\t<Nodes>\n");
//			 for (GridNodeWithActivity n : r.getNodesView()) {
//				 sb.append("\t\t\t<Node>\n");
//				 sb.append("\t\t\t\t<BasicName>").append(n.getBasicName()).append("</BasicName>\n");
//				 sb.append("\t\t\t\t<Activity>").append(n.getActivity().toString()).append("</Activity>\n");
//				 sb.append("\t\t\t</Node>\n");
			sb.append("\t</RoundTrip>\n");
		}
//		sb.append("\t\t</Nodes>\n");
		sb.append("</MultiRoundTrip>");

		return sb.toString();
	}
	
	public static void writeFinalState(String xmlFilePathString, MultiRoundTrip<Node> finalState, long lastIteration) 
	        throws java.io.IOException {
	    StringBuilder sb = new StringBuilder();
	    sb.append("<MultiRoundTrip iteration=\"").append(lastIteration).append("\">\n");
	    sb.append("\t<SamplingWeights>\n");
//		     if (this.samplingWeights != null) {
//		         for (var samplingWeight : weights.getComponentsView()) {
//		             sb.append("\t\t<Weight name=\"")
//		                     .append(samplingWeight.name())
//		                     .append("\">")
//		                     .append(samplingWeight.logWeight(finalState))
//		                     .append("</Weight>\n");
//		         }
//		     }
	    sb.append("\t</SamplingWeights>\n");
	    for (var r : finalState) {
	        sb.append("\t<RoundTrip index=\"").append(r.getIndex()).append("\">\n");
	        sb.append("\t\t<State>\n");
	        sb.append("\t\t\t").append(r.toString()).append("\n");
	        sb.append("\t\t</State>\n");
	        sb.append("\t\t<Episodes>\n");
	        
	        for (Object obj : r.getEpisodes()) {
	            var e = (se.vti.roundtrips.simulator.Episode) obj;
	            sb.append("\t\t\t<").append(e.getClass().getSimpleName())
	              .append(" start=\"").append(e.getEndTime_h() - e.getDuration_h()).append("\"")
	              .append(" end=\"").append(e.getEndTime_h()).append("\"");
	            
	            if (e instanceof se.vti.roundtrips.simulator.StayEpisode) {
	                var n = ((se.vti.roundtrips.simulator.StayEpisode<?>) e).getLocation();
	                sb.append(" loc=\"").append(n.getName()).append("\"");
//	                  .append(" activity=\"").append(n.getActivity().toString()).append("\"");
	            } else if (e instanceof se.vti.roundtrips.simulator.MoveEpisode) {
	                var m = (se.vti.roundtrips.simulator.MoveEpisode<?>) e;
	                sb.append(" from=\"").append(m.getOrigin().getName()).append("\"")
	                  .append(" to=\"").append(m.getDestination().getName()).append("\"")
	                  .append(" duration=\"").append(m.getDuration_h()).append("\"");
	            }
	            sb.append("/>\n");
	        }
	        sb.append("\t\t</Episodes>\n");
	        sb.append("\t</RoundTrip>\n");
	    }
	    sb.append("</MultiRoundTrip>\n");

	    java.nio.file.Path xmlFilePath = java.nio.file.Path.of(xmlFilePathString);
	    String content = java.nio.file.Files.readString(xmlFilePath);
	    String closing = "</MultiRoundTrips>";
	    int pos = content.lastIndexOf(closing);
	    if (pos < 0) {
	        // Fallback: append
	        java.nio.file.Files.writeString(xmlFilePath, sb.toString(), java.nio.file.StandardOpenOption.APPEND);
	        return;
	    }
	    String updated = content.substring(0, pos) + sb + closing + content.substring(pos + closing.length());
	    java.nio.file.Files.writeString(xmlFilePath, updated);
	}
}
