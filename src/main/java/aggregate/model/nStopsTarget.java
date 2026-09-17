package aggregate.model;

import java.util.LinkedHashMap;
import java.util.Map;

import se.vti.roundtrips.common.Node;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.roundtrips.multiple.grouping.PopulationGroupFilter;
import se.vti.roundtrips.samplingweights.calibration.TargetDeviationWeight;
import se.vti.roundtrips.single.RoundTrip;
import se.vti.utils.misc.Tuple;

/**
 * @author frupprecht
 */

public class nStopsTarget<L extends Node> extends TargetDeviationWeight<L> {

	// ---------- Fields ----------------

	private int upperBoundStayEpisodes;
	private double[] target;


	// ---------- Construction ----------------

	public nStopsTarget(double realPopulationSize, int upperBoundStayEpsiodes) {
		super(realPopulationSize);
		this.upperBoundStayEpisodes = upperBoundStayEpsiodes;
	}


	// ---------- Functionality ----------------

	public void setTestTarget() {
		target = new double[upperBoundStayEpisodes + 1];
		target[0] = 0;
		target[1] = 0.08;
		target[2] = 0.40;
		target[3] = 0.15;
		target[4] = 0.19;
		target[5] = 0.07;
		target[6] = 0.06;
		target[7] = 0.02;
		target[8] = 0.01;
	}
	
	public void setTarget(double[] target) {
		this.target = target;
	}


	@Override
	public double[] computeTarget() {
		return target;
	}


	@Override
	public double[] computeSample(MultiRoundTrip<L> multiRoundTrip, PopulationGroupFilter<L> filter) {

		if (filter != null) {
			throw new RuntimeException("Don't filter, use OD summary per population group instead.");
		}

		double[] sample = new double[upperBoundStayEpisodes + 1];

		for (RoundTrip<L> rt : multiRoundTrip) {
			int nStops = rt.size();
			sample[nStops]++;
		}
		return sample;
	}


	@Override
	public String[] createLabels() {
		String[] result = new String[this.target.length];

		for (int i = 0; i < this.target.length; i++) {
			result[i] = "nStops=" + i;
		}
		return result;
	}
}
