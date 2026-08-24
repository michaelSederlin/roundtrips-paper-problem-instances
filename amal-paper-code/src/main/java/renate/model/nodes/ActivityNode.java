package renate.model.nodes;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import renate.model.Activity;
import se.vti.roundtrips.common.Node;

public class ActivityNode extends Node implements ActivityNodeInterface {
    protected final Double x;
    protected final Double y;

    public ActivityNode(String basicName, Double x, Double y, List<Activity> activities) {
        super(basicName, activities);
        Objects.requireNonNull(activities);
        this.x = x;
        this.y = y;
    }

    public ActivityNode withSpecs() {return this;}

    @Override
    public Double getX() {
        return this.x;
    }

    @Override
    public Double getY() {
        return this.y;
    }
    
    @Override
    public List<Activity> getAllActivities() {
        return (List<Activity>) Collections.unmodifiableList(this.getLabels());
    }

    @Override
    public Activity getPrimaryActivity() {
        throw new UnsupportedOperationException("Unimplemented method 'getPrimaryActivity'");
    }


    
}
