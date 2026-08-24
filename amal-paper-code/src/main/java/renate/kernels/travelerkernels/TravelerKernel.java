package renate.kernels.travelerkernels;

import renate.kernels.commons.Kernel;
import renate.kernels.commons.KernelSummary;
import renate.model.Person;
import renate.model.nodes.NodeWithActivity;
import se.vti.roundtrips.single.RoundTrip;

public class TravelerKernel implements Kernel<TravelerKernel.Features> {

    private static final double[] calibratedParameters = new double[]{25.64142600118641, 1029636.1920595355, 0.20652283215354872};
//    private final Person observedPerson;
    private double incomeBW2;
    private double ageBW2;
    private double genderMismatchWeight;
    private double[] parameters;
    final Features observations;

    public static record Features(Integer age, Long income, Integer gender) 
        implements renate.kernels.commons.Features {
        public static Features from(Person p) {
            return new Features(p.getAge(), p.getIncome(), p.getGender());
        }
    }

    public TravelerKernel(Person observedPerson) {this(observedPerson, calibratedParameters);}
    public TravelerKernel(Person observedPerson, double[] parameters) {
        this.observations = new Features(observedPerson.getAge(), observedPerson.getIncome(), observedPerson.getGender());
        setParameters(parameters);
    }

    public TravelerKernel(Features observations) {
        this.observations = observations;
        setParameters(calibratedParameters);
    }

    public void setAgeBW(double bw) {setParameters(new double[]{bw, parameters[1], parameters[2]});}
    public double getAgeBW() { return parameters[0]; }

    public void setIncomeBW(double bw) { setParameters(new double[]{parameters[0], bw, parameters[2]}); }
    public double getIncomeBW() {return parameters[1];}

    public void setGenderMismatch(double mismatch) {setParameters(new double[]{parameters[0], parameters[1], mismatch});}
    public double getGenderMismatch() {return this.parameters[2];}

    public double compute(TravelerKernel.Features syntheticTraveler) {
        double logSimilarity = 0.0;

        if (this.observations.income() != null && syntheticTraveler.income() != null) {
            double incDiff = Math.abs(observations.income() - syntheticTraveler.income());
            double incomeWeight = -incDiff * incDiff / (2 * incomeBW2);
            logSimilarity += incomeWeight;
        }

        if (this.observations.age() != null &&  syntheticTraveler.age() != null) {
            double ageDiff = Math.abs(observations.age() - syntheticTraveler.age());
            double ageWeight = -ageDiff * ageDiff / (2 * ageBW2);
            logSimilarity += ageWeight;
        }

        if (this.observations.gender() != null &&  syntheticTraveler.gender() != null) {
            int genderDiff = (observations.gender().equals(syntheticTraveler.gender())) ? 0 : 1;
            double genderWeight = (genderDiff == 0) ? 1.0 : genderMismatchWeight;
            logSimilarity += Math.log(genderWeight);
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
        this.ageBW2 = parameters[0] * parameters[0];
        this.incomeBW2 = parameters[1] * parameters[1];
        this.genderMismatchWeight = parameters[2];

    }
    @Override
    public void configureRequiredSummary(KernelSummary summary) {
        // TODO Auto-generated method stub
    }
}