package renate.stateprocessors;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import renate.model.Activity;
import renate.model.SurveyResponse;
import renate.model.nodes.ActivityNode;
import se.vti.utils.misc.metropolishastings.MHToFileLogger;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.simulator.MoveEpisode;
import se.vti.roundtrips.simulator.StayEpisode;
import se.vti.roundtrips.single.RoundTrip;

public class VariablesLogger<N extends ActivityNode> extends MHToFileLogger<MultiRoundTrip<N>> {
    // List<SurveyResponse<N>> responses;

    public VariablesLogger(long samplingInterval, String logFile) {
        super(samplingInterval, logFile);
        // this.responses = responses;
    }

    public VariablesLogger(long samplingInterval, java.nio.file.Path logFile) {
        this(samplingInterval, logFile.toString());
    }

    @Override
    public String createHeaderLine() {
        StringBuilder header = new StringBuilder();
        header.append("iteration");
        header.append("\t").append("idx");
        // header.append("\t").append("ooh");
        header.append("\t").append("ttt");
        header.append("\t").append("nStops");
        header.append("\t").append("firstDep");
        header.append("\t").append("lastRet");
        header.append("\t").append("activityCounts");
        for (Activity act : Activity.values()) { header.append("\t" + act.name().toLowerCase() + "Time"); }
        return header.toString();
    }

    @Override
    public String createDataLine(MultiRoundTrip<N> mrt, double lw) {
        StringBuilder dataLine = new StringBuilder();
        for (RoundTrip<N> rt : mrt) {
            dataLine.append(this.iteration());

            Map<Activity, Double> activityTime = new EnumMap<>(Activity.class);
            Map<Activity, Integer> activityCounts = new EnumMap<>(Activity.class);

            for (Activity act : Activity.values()) { 
                activityCounts.put(act, 0);
                activityTime.put(act, 0.0); 
            }
            

            double firstDeparture = Double.MAX_VALUE;
            double lastReturn = Double.MIN_VALUE;
            boolean hasLeftHome = false;

            double ttt = 0.0;
            for (var ep : rt.getEpisodes()) {
                if (ep instanceof MoveEpisode) {
                    MoveEpisode<N> moveEpisode = (MoveEpisode<N>) ep;

                    ttt += moveEpisode.getDuration_h();
                    double endTime = moveEpisode.getEndTime_h();
                    double startTime = endTime - moveEpisode.getDuration_h();
                    if (!hasLeftHome) {
                        firstDeparture = Math.min(firstDeparture, startTime);
                        hasLeftHome = true;
                    }

                    lastReturn = Math.max(lastReturn, endTime);
                    
                } else if (ep instanceof StayEpisode) {
                    StayEpisode<N> step = (StayEpisode<N>) ep;
                    
                    // Will not work well for the labeles network approach since a given node can represent multiple activities
                    // Need to find another approach for counting activity time then
                    // Should also consider how to count the correct home location. 
                    for (Activity act : Activity.values()) {
                        if (step.getLocation().getAllActivities().contains(act)) {
                            activityTime.put(act, activityTime.get(act) + step.getDuration_h());
                            activityCounts.put(act, activityCounts.get(act) + 1);
                        }
                    }
                }
            }

            double firstDepartureTime = (firstDeparture == Double.MAX_VALUE) ? 0.0 : firstDeparture;
            double lastReturnTime = (lastReturn == Double.MIN_VALUE) ? 0.0 : lastReturn;
            int nStops = rt.getNodesView().size();
            // Format activity counts as Python dict literal: {'WORK': 2, 'SERVICES': 1, ...}
            StringBuilder actCountsStr = new StringBuilder("{");
            boolean first = true;
            for (Activity act : Activity.values()) {
                int count = activityCounts.get(act);
                // if (count > 0) {  // Only include activities that were visited
                if (!first) actCountsStr.append(", ");
                actCountsStr.append("'").append(act.name()).append("': ").append(count);
                first = false;
                // }
            }
            actCountsStr.append("}");


            dataLine.append("\t").append(rt.getIndex());
            dataLine.append("\t").append(ttt);
            dataLine.append("\t").append(nStops);
            dataLine.append("\t").append(firstDepartureTime);
            dataLine.append("\t").append(lastReturnTime);
            dataLine.append("\t").append(actCountsStr);
            for (Activity act : Activity.values()) { dataLine.append("\t").append(activityTime.get(act)); }
//            for (Activity act : Activity.values()) { dataLine.append("\t").append(activityTime.get(act)); }

            dataLine.append("\n");
        }

        return dataLine.toString().stripTrailing();
    }
}