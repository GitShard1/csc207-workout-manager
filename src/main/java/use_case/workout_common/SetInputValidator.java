package use_case.workout_common;

public final class SetInputValidator {
    private SetInputValidator() {
    }

    /**
     Checks one completed set. Returns the first problem, or empty string when correct.
     */
    public static String checkSet(double weight, int reps) {

        if (Double.isNaN(weight) || Double.isInfinite(weight)) {
            return "Weight must be a number.";
        }
        else if (weight < 0) {
            return "Weight can't be negative.";
        }
        else if (reps < 1) {
            return "Reps must be at least 1.";
        }
        else {
            return "";
        }
    }
}
