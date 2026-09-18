package aggregate.model;

import se.vti.roundtrips.common.Node;
import se.vti.roundtrips.samplingweights.calibration.TargetDeviationWeight;

abstract class DurationDepartureTarget extends TargetDeviationWeight<Node> {

	// ------------- Fields ------------- 
	final int timeBins = 24;
	final double[][] targetDurationDepartureMatrix  = new double[this.timeBins][this.timeBins];
	
	
	// ------------- Construction ------------- 
	public DurationDepartureTarget(double realPopulationSize) {
		super(realPopulationSize);
	}
	
	
	// ------------- Implementation -------------
	public void setDDEntry(int row, int col, double value) {
		this.targetDurationDepartureMatrix[row][col] = value;
	}

	public double[][] getTargetDDMatrix() {
		return this.targetDurationDepartureMatrix;
	}

	double[] dd2Array(double [][] dd) {
		double[] result = new double[this.timeBins * this.timeBins];
		int index = 0;		
		for (int row = 0; row < this.timeBins; row++) {			
			System.arraycopy(dd[row], 0, result, index, dd[row].length);
			index += dd[row].length;
		}
		return result;
	}

	public double[] computeTarget() {
		return this.dd2Array(this.targetDurationDepartureMatrix);
	}

	public String[] createLabels() {
		String[] result = new String[this.timeBins * this.timeBins];		
		int i = 0;		
		for ( int row = 0; row < this.timeBins; row++ ) {			
			for (int col = 0; col < this.timeBins; col++) {
				result[i] = "dur"+ row + "-" + "dep" + col;			
				i++;
			}			
		}
		return result;
	}
}
