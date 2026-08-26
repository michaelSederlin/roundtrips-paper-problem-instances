package disaggregate.weights;

import disaggregate.model.Person;

public class NumberOfStopsKernel extends ScalarGaussianKernel {

    private final int observedNumStops;

    public NumberOfStopsKernel(Person person) { this(person.getTrip().size()); }
    public NumberOfStopsKernel(Person person, double lambda) { this(person.getTrip().size(), lambda); }
    public NumberOfStopsKernel(int observedNumStops) { this(observedNumStops, 1.0); }
    public NumberOfStopsKernel(int observedNumStops, double lambda) {
        super(lambda);
        this.observedNumStops = observedNumStops;
    }

    @Override
    public double compute(KernelFeatures features) {
        double diff = observedNumStops - features.nStops();
        return gaussian(diff);
    }
}
