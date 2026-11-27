package use_case.plan_common;

import entity.BodyPart;
import entity.Exercise;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannedExerciseBuilderTest {
    private static final Exercise BENCH_PRESS = new Exercise("0025", "Barbell Bench Press", "A chest press.",
            BodyPart.CHEST, "Pectorals", List.of(), "Barbell", "compound", List.of());
    private static final Exercise SQUAT = new Exercise("0043", "Barbell Squat", "A leg exercise.",
            BodyPart.UPPER_LEGS, "Quads", List.of(), "Barbell", "compound", List.of());

    private final int[] lookups = {0};
    private final ExerciseLookupDataAccessInterface exerciseLookup = exerciseId -> {
        lookups[0]++;
        return Map.of("0025", BENCH_PRESS, "0043", SQUAT).get(exerciseId);
    };

    private record Row(String exerciseId, int sets, int reps, Double weight) implements PlanRow {
    }

    @Test
    void buildsEveryRowInOrderTest() {
        PlannedExerciseBuilder.Result result = PlannedExerciseBuilder.build(
                List.of(new Row("0043", 5, 5, 100.0), new Row("0025", 3, 8, null)), exerciseLookup);

        assertEquals("", result.error());
        assertEquals(2, result.exercises().size());
        assertSame(SQUAT, result.exercises().get(0).getExercise());
        assertEquals(5, result.exercises().get(0).getTargetSets());
        assertEquals(5, result.exercises().get(0).getTargetReps());
        assertEquals(100.0, result.exercises().get(0).getTargetWeight());
        assertSame(BENCH_PRESS, result.exercises().get(1).getExercise());
        assertEquals(3, result.exercises().get(1).getTargetSets());
        assertEquals(8, result.exercises().get(1).getTargetReps());
        assertNull(result.exercises().get(1).getTargetWeight());
    }

    @Test
    void emptyRowsTest() {
        PlannedExerciseBuilder.Result result = PlannedExerciseBuilder.build(List.of(), exerciseLookup);

        assertEquals("", result.error());
        assertTrue(result.exercises().isEmpty());
    }

    @Test
    void missingExerciseTest() {
        PlannedExerciseBuilder.Result result = PlannedExerciseBuilder.build(
                List.of(new Row("0025", 3, 8, 60.0), new Row("9999", 3, 8, null)), exerciseLookup);

        assertEquals("Row 2: exercise not found.", result.error());
        assertTrue(result.exercises().isEmpty());
    }

    @Test
    void stopsAtTheFirstMissingExerciseTest() {
        PlannedExerciseBuilder.Result result = PlannedExerciseBuilder.build(
                List.of(new Row("9999", 3, 8, null), new Row("0025", 3, 8, 60.0)), exerciseLookup);

        assertEquals("Row 1: exercise not found.", result.error());
        assertEquals(1, lookups[0]);
    }
}
