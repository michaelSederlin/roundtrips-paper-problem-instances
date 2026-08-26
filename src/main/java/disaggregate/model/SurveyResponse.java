package disaggregate.model;

import disaggregate.weights.CompositeKernel;
import disaggregate.weights.TravelerKernel;

public class SurveyResponse<N extends ActivityNode> {
    private final Person respondent;
    private final CompositeKernel kernel;

    public SurveyResponse(Person respondent, CompositeKernel kernel) {
        this.respondent = respondent;
        this.kernel = kernel;
    }

    public double matchesRespondentWeight(Person syntheticPerson) {
        TravelerKernel travelerKernel = this.kernel.getKernel(TravelerKernel.class);
        return travelerKernel.compute(syntheticPerson);
    }

    public CompositeKernel getKernel() {
        return this.kernel;
    }

    public Person getRespondent() {
        return this.respondent;
    }
}
