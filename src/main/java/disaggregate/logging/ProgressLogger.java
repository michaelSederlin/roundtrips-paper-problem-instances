package disaggregate.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import se.vti.roundtrips.common.Node;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.utils.misc.metropolishastings.MHAbstractStateProcessor;

public class ProgressLogger<N extends Node> extends MHAbstractStateProcessor<MultiRoundTrip<N>> {
    private long iter;
    private final long msgInt;
    private final String identifier;
    private final Logger logger = LogManager.getLogger(ProgressLogger.class);
    private final long t0 = System.currentTimeMillis();
    private double previousLW;
    
    public ProgressLogger(long samplingInterval) { this(samplingInterval, null); }
    public ProgressLogger(long samplingInterval, String identifier) {
        super(0, samplingInterval);
        this.msgInt = samplingInterval;
        this.identifier = identifier;
        previousLW = Double.NEGATIVE_INFINITY;
    }

    @Override
    public void processStateHook(MultiRoundTrip<N> state, double logWeight) {
        iter += this.msgInt;
        double lwDiff = previousLW - logWeight;
        double rate = iter / Math.max(1e-9, (System.currentTimeMillis() - t0) / 1000.0);

        logger.info("{} iter={} logWeight={} logWeightDiff={}   ({} it/s)",
                this.identifier, this.iter, logWeight, lwDiff, String.format("%.0f", rate));
        this.previousLW = logWeight;
        }
}
