package disaggregate.model.weights;

import disaggregate.model.ActivityNode;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.utils.misc.metropolishastings.MHWeight;

public interface ResponseWeight<N extends ActivityNode> extends MHWeight<MultiRoundTrip<N>> {
    public double[][] getLastScheduleKernels();
    public double[][] getLastLocationKernels();
    public double[][] getPersonResponseWeights();
}
