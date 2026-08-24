package disaggregate.weights;

import renate.model.Person;
import renate.model.nodes.NodeWithActivity;
import se.vti.roundtrips.single.RoundTrip;

public class TravelerKernel implements Kernel {

    private double lambda_income;
    private double lambda_age;
    private double eta;
    
    private double lambda_income2;
    private double lambda_age2;
    private double eta2;

    private double income;
    private double age;
    private double gender;

    private double[] parameters;
    final Person observedPerson;
   
    public TravelerKernel(Person observedPerson) {
        this.observedPerson = observedPerson;
    }

    public TravelerKernel(Person observedPerson, double[] parameters) {
        this.observedPerson = observedPerson;
        setParameters(parameters);
    }

    public TravelerKernel(Features observations) {
        this.observations = observations;
        setParameters(calibratedParameters);
    }

    public void setAgeBW(double bw) { setParameters(new double[]{bw, parameters[1], parameters[2]}); }
    public double getAgeBW() { return lambda_age;}

    public void setIncomeBW(double bw) { setParameters(new double[]{parameters[0], bw, parameters[2]}); }
    public double getIncomeBW() { return lambda_income; }

    public void setGenderMismatch(double mismatch) { setParameters(new double[]{parameters[0], parameters[1], mismatch}); }
    public double getGenderMismatch() { return eta; }

    public double compute(Person syntheticTraveler) {
        double logSimilarity = 0.0;

        if (this.observedPerson.getIncome() != null && syntheticTraveler.getIncome() != null) {
            double incDiff = Math.abs(observedPerson.getIncome() - syntheticTraveler.getIncome());
            double incomeWeight = -incDiff * incDiff / (2 * lambda_income2);
            logSimilarity += incomeWeight;
        }

        if (this.observedPerson.getAge() != null &&  syntheticTraveler.getAge() != null) {
            double ageDiff = Math.abs(observedPerson.getAge() - syntheticTraveler.getAge());
            double ageWeight = -ageDiff * ageDiff / (2 * lambda_age2);
            logSimilarity += ageWeight;
        }

        if (this.observedPerson.getGender() != null &&  syntheticTraveler.getGender() != null) {
            int genderDiff = (observedPerson.getGender().equals(syntheticTraveler.getGender())) ? 0 : 1;
            double genderWeight = -genderDiff * genderDiff / (2 * eta2);
            // double genderWeight = (genderDiff == 0) ? 1.0 : genderMismatchWeight;
            logSimilarity += genderWeight;
        }
        
        double result = Math.exp(logSimilarity);
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            throw new RuntimeException("Numerical instability in TravelerKernel.compute: logSimilarity = " + logSimilarity);
        }
        return result; 
    }

    @Override
    public double compute(RoundTrip<NodeWithActivity> rt, KernelSummary summary) {
        // TravelerKernel uses static Person demographics, not RoundTrip data
        // It should be pre-computed in ExplainRoundTripsByResponses constructor
        throw new UnsupportedOperationException("TravelerKernel does not use RoundTrip data - use pre-computed person-response weights");
    }

    @Override
    public Features extractFeatures(RoundTrip<NodeWithActivity> rt) {
        // TravelerKernel doesn't use RoundTrip for feature extraction
        throw new UnsupportedOperationException("TravelerKernel extracts features from Person objects only");
    }

    @Override
    public Features extractFeatures(Person p) {
        return Features.from(p);
    }

    @Override
    public double[] getParameters() {
        return this.parameters;
    }

    @Override
    public void setParameters(double[] params) {  // ageBW, incomeBW, genderMismatch
        this.parameters = params;

        this.lambda_age = params[0];
        this.lambda_income = params[1];
        this.eta = params[2];

        this.lambda_age2 = this.lambda_age * this.lambda_age;
        this.lambda_income2 = this.lambda_income * this.lambda_income;
        this.eta2 = this.eta * this.eta;
    }

    @Override
    public void configureRequiredSummary(KernelSummary summary) {
        // TODO Auto-generated method stub
    }
}