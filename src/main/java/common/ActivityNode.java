package renate.model.nodes;

import java.util.List;

import renate.model.Activity;

public class ActivityNode {
    private final Double x;
    private final Double y;
    private Activity activity;

    public ActivityNode(int name, double x, double y, Activity activity) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.activity = activity;
    }    

    public Activity getActivity() { return this.activity; }
    public Double getX() { return this.x; }
    public Double getY() { return this.y; }

    public Double computeDistance(ActivityNodeInterface other) {
        if (this.getX() == null || this.getY() == null || other.getX() == null || other.getY() == null) {
            throw new IllegalStateException("Cannot compute distance with null coordinates");
        }
        Double xDiff = this.getX() - other.getX();
        Double yDiff = this.getY() - other.getY();

        Double dist = Math.sqrt(xDiff * xDiff + yDiff * yDiff);
        return dist;
    }
}
