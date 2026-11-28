package interface_adapter.log_workout;

import use_case.workout_common.SetEntry;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Turns weights, sets and targets into the text the workout screens show. */
public final class WorkoutFormat {
    private WorkoutFormat() {
    }

    public static String weight(double kg) {
        return BigDecimal.valueOf(kg).stripTrailingZeros().toPlainString();
    }

    public static String set(double kg, int reps) {
        return weight(kg) + " kg x " + reps;
    }

    public static String sets(List<SetEntry> sets) {
        final ArrayList<String> formattedSets = new ArrayList<>();
        for (SetEntry setEntry : sets) {
            formattedSets.add(set(setEntry.weight(), setEntry.reps()));
        }
        return String.join(", ", formattedSets);
    }

    public static String target(int sets, int reps, Double kg) {
        final String target = sets + " x " + reps;
        if (kg == null) {
            return target;
        }
        return target + " at " + weight(kg) + " kg";
    }
}
