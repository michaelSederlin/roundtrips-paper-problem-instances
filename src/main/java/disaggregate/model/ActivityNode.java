package disaggregate.model;

import java.util.List;
import common.Activity;

import se.vti.roundtrips.common.Node;

public class ActivityNode extends Node {
    private Activity activity;
    private Double x;
    private Double y;

    public ActivityNode(int name, double x, double y, Activity activity) {
        super(String.valueOf(name), List.of(activity));
        this.x = x;
        this.y = y;
        this.activity = activity;
    } 

    public ActivityNode(String name, double x, double y, Activity activity) {
        super(name, List.of(activity));
        this.x = x;
        this.y = y;
        this.activity = activity;
    }

    public Double computeDistance(ActivityNode other) {
        if (this.getX() == null || this.getY() == null || other.getX() == null || other.getY() == null) {
            throw new IllegalStateException("Cannot compute distance with null coordinates");
        }
        Double xDiff = this.getX() - other.getX();
        Double yDiff = this.getY() - other.getY();

        Double dist = Math.sqrt(xDiff * xDiff + yDiff * yDiff);
        return dist;
    }    

    public Activity getPrimaryActivity() { return this.activity; }
    public List<Activity> getAllActivities() { return List.of(this.activity); }

    public Double getX() { return this.x; }
    public Double getY() { return this.y; }
    public Activity getActivity() { return this.activity; }
}
