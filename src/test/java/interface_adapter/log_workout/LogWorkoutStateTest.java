package interface_adapter.log_workout;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogWorkoutStateTest {
    private final UUID planId = UUID.randomUUID();
    private final List<LogWorkoutState.ExerciseRow> exercises = List.of(
            new LogWorkoutState.ExerciseRow("0025", "Barbell Bench Press", "3 x 8 at 60 kg", "60 kg x 8",
                    List.of(new LogWorkoutState.SetRow("60", "8"), new LogWorkoutState.SetRow("60", "8"))));
    private final LogWorkoutState state = new LogWorkoutState(planId, "Push Day", exercises, null, false);

    @Test
    void emptyTest() {
        LogWorkoutState empty = LogWorkoutState.empty();

        assertNull(empty.planId());
        assertEquals("", empty.planName());
        assertEquals(List.of(), empty.exercises());
        assertNull(empty.error());
        assertFalse(empty.askDiscard());
    }

    @Test
    void withErrorKeepsTheWorkoutTest() {
        LogWorkoutState withDiscardPrompt = state.withDiscardPrompt();

        LogWorkoutState result = withDiscardPrompt.withError("Exercise 1, set 1: Weight can't be negative.");

        assertEquals(planId, result.planId());
        assertEquals("Push Day", result.planName());
        assertEquals(exercises, result.exercises());
        assertEquals("Exercise 1, set 1: Weight can't be negative.", result.error());
        assertFalse(result.askDiscard());
    }

    @Test
    void withDiscardPromptKeepsTheExercisesTest() {
        LogWorkoutState result = state.withError("Something went wrong.").withDiscardPrompt();

        assertEquals(planId, result.planId());
        assertEquals("Push Day", result.planName());
        assertEquals(exercises, result.exercises());
        assertNull(result.error());
        assertTrue(result.askDiscard());
    }

    @Test
    void exercisesAreCopiedTest() {
        ArrayList<LogWorkoutState.ExerciseRow> rows = new ArrayList<>(exercises);
        LogWorkoutState copied = new LogWorkoutState(planId, "Push Day", rows, null, false);

        rows.clear();

        assertEquals(exercises, copied.exercises());
    }

    @Test
    void setRowsAreCopiedTest() {
        ArrayList<LogWorkoutState.SetRow> setRows = new ArrayList<>(List.of(new LogWorkoutState.SetRow("60", "8")));
        LogWorkoutState.ExerciseRow row = new LogWorkoutState.ExerciseRow("0025", "Barbell Bench Press",
                "3 x 8 at 60 kg", "", setRows);

        setRows.clear();

        assertEquals(List.of(new LogWorkoutState.SetRow("60", "8")), row.sets());
    }
}
