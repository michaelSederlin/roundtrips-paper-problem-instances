package renate.model;

import renate.kernels.commons.CompositeKernel;
import renate.kernels.travelerkernels.TravelerKernel;
import renate.model.nodes.ActivityNode;

public class SurveyResponse<N extends ActivityNode> {
    private final Person respondent;
    private final CompositeKernel kernel;

    public SurveyResponse(Person respondent, CompositeKernel kernel) {
        this.respondent = respondent;
        this.kernel = kernel;
    }

    public double matchesRespondentWeight(Person syntheticPerson) {
        TravelerKernel travelerKernel = this.kernel.getKernel(TravelerKernel.class);
        var feats = travelerKernel.extractFeatures(syntheticPerson);
        return travelerKernel.compute(feats);
    }

    public CompositeKernel getKernel() {
        return this.kernel;
    }

    public Person getRespondent() {
        return this.respondent;
    }
}
