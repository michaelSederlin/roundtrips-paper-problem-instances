package disaggregate.model.nodes;

import java.util.List;

import disaggregate.model.Activity;

public interface ActivityNodeInterface {

    Double getX(); 
    Double getY();

    default Double computeDistance(ActivityNodeInterface other) {
        if (this.getX() == null || this.getY() == null || other.getX() == null || other.getY() == null) {
            throw new IllegalStateException("Cannot compute distance with null coordinates");
        }
        Double xDiff = this.getX() - other.getX();
        Double yDiff = this.getY() - other.getY();

        Double dist = Math.sqrt(xDiff * xDiff + yDiff * yDiff);
        return dist;
    }

    // default boolean equals(ActivityNodeInterface other) {
    //     return this.getX() == other.getX() && this.getY() == other.getY();
    // }

    Activity getPrimaryActivity();
    List<Activity> getAllActivities();
}
