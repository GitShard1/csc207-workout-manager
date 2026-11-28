package use_case.workout_common;

import entity.BodyPart;
import entity.Exercise;
import entity.ExerciseSet;
import entity.LoggedExercise;
import entity.PlannedExercise;
import entity.User;
import entity.Workout;
import entity.WorkoutPlan;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Shared test data for the workout use cases. */
public final class WorkoutFixtures {
    public static final User USER = new User(UUID.randomUUID(), "amgalan", "Amgalan");
    public static final Exercise BENCH_PRESS = new Exercise("0025", "Barbell Bench Press", "A chest press.",
            BodyPart.CHEST, "Pectorals", List.of(), "Barbell", "compound", List.of());
    public static final Exercise SQUAT = new Exercise("0043", "Barbell Squat", "A leg exercise.",
            BodyPart.UPPER_LEGS, "Quads", List.of(), "Barbell", "compound", List.of());

    private WorkoutFixtures() {
    }

    /** The user's own plan: bench press 3 x 8 at 60 kg, then squat 5 x 5 with no weight. */
    public static WorkoutPlan pushDay() {
        return new WorkoutPlan(UUID.randomUUID(), "Push Day", USER.getUserID(),
                List.of(new PlannedExercise(BENCH_PRESS, 3, 8, 60.0), new PlannedExercise(SQUAT, 5, 5, null)),
                false, false, null);
    }

    public static WorkoutPlan starterPlan() {
        return new WorkoutPlan(UUID.randomUUID(), "Beginner Full Body", UUID.randomUUID(),
                List.of(new PlannedExercise(SQUAT, 3, 10, null)), false, true, null);
    }

    public static WorkoutPlan someoneElsesPlan() {
        return new WorkoutPlan(UUID.randomUUID(), "Pull Day", UUID.randomUUID(),
                List.of(new PlannedExercise(SQUAT, 3, 10, null)), false, false, null);
    }

    public static Workout earlierWorkout(LocalDate date, LoggedExercise... exercises) {
        return new Workout(UUID.randomUUID(), USER.getUserID(), UUID.randomUUID(), "Push Day", date,
                List.of(exercises));
    }

    public static LoggedExercise logged(Exercise exercise, ExerciseSet... sets) {
        return new LoggedExercise(exercise, List.of(sets));
    }
}
