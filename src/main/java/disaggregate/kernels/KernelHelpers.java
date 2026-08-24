package disaggregate.kernels;

import java.util.List;

import se.vti.roundtrips.simulator.Episode;
import se.vti.utils.misc.Tuple;

public class KernelHelpers {
    public static double overlapWithWrap(double startTime1, double endTime1, double startTime2, double endTime2, double H) {
        double dur1 = Math.max(0.0, endTime1 - startTime1);
        double dur2 = Math.max(0.0, endTime2 - startTime2);

        List<Tuple<Double, Double>> obsIntervals = Episode.effectiveIntervals(dur2, endTime2, H); 

        Episode simEp = new Episode(); 
        simEp.setDuration_h(dur1); 
        simEp.setEndTime_h(endTime1);
        return simEp.overlap_h(obsIntervals, H);
    }
}