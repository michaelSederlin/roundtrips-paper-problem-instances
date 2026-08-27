package common;

import se.vti.utils.misc.metropolishastings.MHAbstractStateProcessor;

public class IterationCounter<X> extends MHAbstractStateProcessor<X> {
	private long iter = -1; 
	public IterationCounter() {
		super(0, 1);
	}
	@Override
	public void start() {
		super.start();
		iter = -1;
	}
	@Override
	public void processStateHook(X state, double logWeight) {
		iter++;
	}
	public long getLastIteration() {
		return iter;
	}
}