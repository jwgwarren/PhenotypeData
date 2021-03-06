package org.mousephenotype.cda.enumerations;

public enum LifeStage {
    E9_5("E9.5"),
    E12_5("E12.5"),
    E15_5("E15.5"),
    E18_5("E18.5"),
    EARLY_ADULT("Early adult"),
    MIDDLE_AGED_ADULT("Middle aged adult"),
    LATE_ADULT("Late adult");

    private final String name;

    LifeStage(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public static LifeStage getByDisplayName(String displayName) {
        switch (displayName.toLowerCase()) {
            case "e9.5":
            case "embryonic day 9.5":
                return LifeStage.E9_5;
            case "e12.5":
            case "embryonic day 12.5":
                return LifeStage.E12_5;
            case "e15.5":
            case "embryonic day 15.5":
                return LifeStage.E15_5;
            case "e18.5":
            case "embryonic day 18.5":
                return LifeStage.E18_5;
            case "early adult":
                return LifeStage.EARLY_ADULT;
            case "middle aged adult":
                return LifeStage.MIDDLE_AGED_ADULT;
            case "late adult":
                return LifeStage.LATE_ADULT;
            default:
                throw new IllegalArgumentException("No enum constant " + LifeStage.class + "." + displayName);
        }
    }

}
