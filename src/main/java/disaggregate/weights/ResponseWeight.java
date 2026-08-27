package disaggregate.weights;

import common.GridNodeWithActivity;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.utils.misc.metropolishastings.MHWeight;

public interface ResponseWeight extends MHWeight<MultiRoundTrip<GridNodeWithActivity>> {
    public double[][] getLastScheduleKernels();
    public double[][] getLastLocationKernels();
    public double[][] getPersonResponseWeights();
}
