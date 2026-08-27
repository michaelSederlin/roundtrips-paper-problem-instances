package disaggregate.model;

import java.util.Map;
import java.util.HashMap;

public enum Activity {
    HOME(1, 0.0, 24.0, 0.0, 9.0),
    WORK(2, 7.0, 21.0, 0.0, 0.0),
    EDUCATION(3, 7.0, 21.0, 0.0, 0.0),
     SOCIAL(4, 0.0, 24.0, 0.0, 0.0),
    SERVICES(5, 0.0, 24.0, 0.0, 0.0),
//    DIGITAL(6,0.0, 24.0, 0.0, 0.0),
    LEISURE(7, 0.0, 24.0, 0.0, 0.0),
    OTHER(8, 0.0, 24.0, 0.0, 0.0);

    final int priority;
    final Spec specs;
    record Spec(double open, double close, double minEach, double minOnce) {}

    Activity(int priority, double open, double close, double minEach, double minOnce) {
        this.priority = priority;
        this.specs = new Spec(open, close, minEach, minOnce);
    }

    public int getPriority() { return priority; }
    public Spec getSpecs() { return specs; }

    private static final Map<String, Activity> codeMapping = new HashMap<>();
    static {
        // WORK
        for (String k : new String[]{"2", "80", "81", "82", "4", "34", "96", "97", "98"}) codeMapping.put(k, WORK);
        // EDUCATION
        for (String k : new String[] {"3", "85", "86", "87", "5", "35"}) codeMapping.put(k, EDUCATION);
        // SERVICES
        for (String k : new String[] {"6", "7", "8", "9", "10", "12", "33", "11", "13", "23"}) codeMapping.put(k, SERVICES);
        // SOCIAL
        // for (String k: new String[] {"14", "24"}) codeMapping.put(k, SOCIAL);
        // LEISURE
        for (String k : new String[] {"16", "17", "15", "18", "19", "20", "21", "22"}) codeMapping.put(k, LEISURE);
        // DIGITAL
//        for (String k: new String[] {"36", "37"}) codeMapping.put(k, DIGITAL);
        // OTHER
        for (String k: new String[] {"25", "55"}) codeMapping.put(k, OTHER);
        for (String k: new String[] {".", ".A", ".G", ".H", ".I", ".P", "null"}) codeMapping.put(k, OTHER);
    }

    public static Activity fromRVUCode(String code) {
        return codeMapping.getOrDefault(code, OTHER);
    }

    public static Activity fromString(String activityName) {
        if (activityName == null || activityName.trim().isEmpty()) {
            return OTHER;
        }
        return Activity.valueOf(activityName.toUpperCase());
    }
}
