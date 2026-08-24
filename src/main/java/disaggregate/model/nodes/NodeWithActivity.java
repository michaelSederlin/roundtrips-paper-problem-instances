package disaggregate.model.nodes;

import java.util.List;

import disaggregate.model.Activity;

public class NodeWithActivity extends ActivityNode {
    private Activity activity;

    public NodeWithActivity(int name, double x, double y, Activity activity) {
        super(String.valueOf(name), x, y, List.of(activity));
        this.activity = activity;
    }    

    public NodeWithActivity(String name, double x, double y, Activity activity) {
        super(name, x, y, List.of(activity));
        this.activity = activity;
    }

    @Override
    public Activity getPrimaryActivity() { return this.activity; }

    public Activity getActivity() {
        return this.activity;
    }
}
