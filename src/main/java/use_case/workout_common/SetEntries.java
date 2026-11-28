package use_case.workout_common;

import entity.ExerciseSet;

import java.util.ArrayList;
import java.util.List;

public final class SetEntries {
    private SetEntries() {
    }

    /** Returns the sets as plain values, in the same order. */
    public static List<SetEntry> from(List<ExerciseSet> sets) {
        final ArrayList<SetEntry> setEntries = new ArrayList<>();
        for (ExerciseSet set : sets) {
            setEntries.add(new SetEntry(set.getWeight(), set.getReps()));
        }
        return setEntries;
    }
}
