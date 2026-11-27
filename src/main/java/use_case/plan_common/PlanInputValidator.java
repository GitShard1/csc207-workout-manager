package use_case.plan_common;

import java.util.List;

public final class PlanInputValidator {
    public static final int MAX_NAME_LENGTH = 50;

    private PlanInputValidator() {
    }

    /**
     Checks the plan name and every exercise row, in order. Returns the first problem, or empty string when correct.
     */
    public static String checkInput(String name, List<? extends PlanRow> rows) {
        final String nameCheck = checkName(name);
        if (!nameCheck.isBlank()) {
            return nameCheck;
        }
        else if (rows == null || rows.isEmpty()) {
            return "Add at least one exercise.";
        }

        int row = 0;

        for (PlanRow planRow : rows) {
            row += 1;
            final String rowCheck = checkRow(row, planRow);
            if (!rowCheck.isBlank()) {
                return rowCheck;
            }
        }
        return "";
    }

    /**
     Checks the plan name. Returns empty string when correct.
     */
    private static String checkName(String name) {
        if (name == null || name.isBlank()) {
            return "Plan name is required.";
        }
        else if (name.length() > MAX_NAME_LENGTH) {
            return "Plan name must be at most " + MAX_NAME_LENGTH + " characters.";
        }
        return "";
    }

    /**
     Checks one exercise row, numbered from 1. Returns empty string when correct.
     */
    private static String checkRow(int row, PlanRow planRow) {
        if (planRow.exerciseId() == null || planRow.exerciseId().isBlank()) {
            return "Row " + row + ": choose an exercise.";
        }
        else if (planRow.sets() < 1) {
            return "Row " + row + ": must contain at least 1 set.";
        }
        else if (planRow.reps() < 1) {
            return "Row " + row + ": must contain at least 1 rep.";
        }
        else if (planRow.weight() != null && planRow.weight() < 0) {
            return "Row " + row + ": weights cannot be negative.";
        }
        return "";
    }
}
