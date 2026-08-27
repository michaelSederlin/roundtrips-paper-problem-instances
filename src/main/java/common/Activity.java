package common;

public enum Activity {
    HOME, WORK, EDUCATION, OTHER;

    public static Activity fromString(String act) {
        return switch (act.toLowerCase()) {
            case "home" -> HOME;
            case "education" -> EDUCATION;
            case "work" -> WORK; 
            case "other" -> OTHER;
            default -> throw new IllegalArgumentException("Unknown activity: " + act);
        };
    };
}
