package disaggregate.weights;

import disaggregate.model.Person;

public class TravelerKernel implements Kernel {

    private static final double[] calibratedParameters = new double[]{25.64142600118641, 1029636.1920595355, 0.20652283215354872};
    private double incomeBW2;
    private double ageBW2;
    private double genderMismatchWeight;
    private double[] parameters;

    private final Integer observedAge;
    private final Long observedIncome;
    private final Integer observedGender;

    public TravelerKernel(Person observedPerson) {this(observedPerson, calibratedParameters);}
    public TravelerKernel(Person observedPerson, double[] parameters) {
        this.observedAge = observedPerson.getAge();
        this.observedIncome = observedPerson.getIncome();
        this.observedGender = observedPerson.getGender();
        setParameters(parameters);
    }

    public void setAgeBW(double bw) {setParameters(new double[]{bw, parameters[1], parameters[2]});}
    public double getAgeBW() { return parameters[0]; }

    public void setIncomeBW(double bw) { setParameters(new double[]{parameters[0], bw, parameters[2]}); }
    public double getIncomeBW() {return parameters[1];}

    public void setGenderMismatch(double mismatch) {setParameters(new double[]{parameters[0], parameters[1], mismatch});}
    public double getGenderMismatch() {return this.parameters[2];}

    public double compute(Person syntheticTraveler) {
        double logSimilarity = 0.0;

        if (parameters[0] > 0 && this.observedIncome != null && syntheticTraveler.getIncome() != null) {
            double incDiff = Math.abs(this.observedIncome - syntheticTraveler.getIncome());
            double incomeWeight = -incDiff * incDiff / (2 * incomeBW2);
            logSimilarity += incomeWeight;
        }

        if (parameters[1] >  0 && this.observedAge != null && syntheticTraveler.getAge() != null) {
            double ageDiff = Math.abs(this.observedAge - syntheticTraveler.getAge());
            double ageWeight = -ageDiff * ageDiff / (2 * ageBW2);
            logSimilarity += ageWeight;
        }

        if (parameters[2] > 0 && this.observedGender != null && syntheticTraveler.getGender() != null) {
            int genderDiff = (this.observedGender.equals(syntheticTraveler.getGender())) ? 0 : 1;
            double genderWeight = - genderDiff * genderDiff / (2 * genderMismatchWeight);
            logSimilarity += genderWeight;
        }

        double result = Math.exp(logSimilarity);
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            throw new RuntimeException("Numerical instability in TravelerKernel.compute: logSimilarity = " + logSimilarity);
        }
        return result;
    }

    @Override
    public double compute(KernelFeatures features) {
        // TravelerKernel uses static Person demographics, not RoundTrip data
        // It should be pre-computed in ExplainRoundTripsByResponses constructor
        throw new UnsupportedOperationException("TravelerKernel does not use RoundTrip data - use pre-computed person-response weights");
    }

    @Override
    public double[] getParameters() {
        return this.parameters;
    }

    @Override
    public void setParameters(double[] params) {  // ageBW, incomeBW, genderMismatch
        this.parameters = params;
        this.ageBW2 = parameters[0] * parameters[0];
        this.incomeBW2 = parameters[1] * parameters[1];
        this.genderMismatchWeight = parameters[2];

    }
}
