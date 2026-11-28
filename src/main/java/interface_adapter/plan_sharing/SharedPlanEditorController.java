package interface_adapter.plan_sharing;

import entity.Exercise;
import entity.PlannedExercise;
import entity.WorkoutPlan;
import interface_adapter.edit_plan.EditPlanController;
import interface_adapter.open_plan.OpenPlanController;
import interface_adapter.plan_editor.PlanEditorState;
import use_case.account.CurrentUserDataAccessInterface;
import use_case.edit_plan.EditPlanDataAccessInterface;
import use_case.edit_plan.EditPlanInteractor;
import use_case.edit_plan.EditPlanOutputBoundary;
import use_case.edit_plan.EditPlanOutputData;
import use_case.open_plan.OpenPlanInteractor;
import use_case.open_plan.OpenPlanOutputBoundary;
import use_case.open_plan.OpenPlanOutputData;
import use_case.plan_common.AccountBoundPlanDataAccessInterface;
import use_case.plan_common.PlanStorageUnavailableException;

import java.util.List;
import java.util.UUID;

/** Runs an edit or open request against the initiating account, with no shared presenter state. */
public final class SharedPlanEditorController {
    private final AccountBoundPlanDataAccessInterface plans;
    private final CurrentUserDataAccessInterface currentUser;

    public SharedPlanEditorController(AccountBoundPlanDataAccessInterface plans,
                                      CurrentUserDataAccessInterface currentUser) {
        this.plans = plans;
        this.currentUser = currentUser;
    }

    public PlanEditorState open(UUID viewer, UUID planId) {
        final Result result = new Result();
        final OpenPlanInteractor interactor = new OpenPlanInteractor(id -> plans.getByIdFor(viewer, id),
                currentUserFor(viewer), result);
        new OpenPlanController(interactor).execute(planId);
        return result.state;
    }

    public PlanEditorState save(UUID viewer, UUID planId, String name, List<String> exerciseIds, List<Integer> sets,
                                List<Integer> reps, List<Double> weights) {
        final ScopedPlanEdit store = new ScopedPlanEdit(viewer);
        final Result result = new Result();
        final EditPlanInteractor interactor = new EditPlanInteractor(store, store::findStoredExercise,
                currentUserFor(viewer), result);
        new EditPlanController(interactor).execute(planId, name, exerciseIds, sets, reps, weights);
        return result.state;
    }

    private CurrentUserDataAccessInterface currentUserFor(UUID viewer) {
        return () -> currentUser.currentUser().filter(user -> user.getUserID().equals(viewer));
    }

    private final class ScopedPlanEdit implements EditPlanDataAccessInterface {
        private final UUID viewer;
        private WorkoutPlan loaded;

        private ScopedPlanEdit(UUID viewer) {
            this.viewer = viewer;
        }

        @Override
        public WorkoutPlan getById(UUID planId) throws PlanStorageUnavailableException {
            loaded = plans.getByIdFor(viewer, planId);
            return loaded;
        }

        @Override
        public boolean existsByName(UUID ownerId, String planName) throws PlanStorageUnavailableException {
            return plans.existsByName(ownerId, planName);
        }

        @Override
        public void update(WorkoutPlan workoutPlan) throws PlanStorageUnavailableException {
            plans.update(workoutPlan);
        }

        private Exercise findStoredExercise(String exerciseId) {
            return loaded.getExercises().stream()
                    .map(PlannedExercise::getExercise)
                    .filter(exercise -> exercise.getExerciseID().equals(exerciseId))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static final class Result implements OpenPlanOutputBoundary, EditPlanOutputBoundary {
        private PlanEditorState state;

        @Override
        public void prepareSuccessView(OpenPlanOutputData data) {
            final List<PlanEditorState.ExerciseRow> rows = data.exercises().stream()
                    .map(row -> new PlanEditorState.ExerciseRow(row.exerciseId(), row.exerciseName(), row.sets(),
                            row.reps(), row.weight()))
                    .toList();
            state = new PlanEditorState(data.planId(), data.name(), rows, null);
        }

        @Override
        public void prepareSuccessView(EditPlanOutputData data) {
            final List<PlanEditorState.ExerciseRow> rows = data.exercises().stream()
                    .map(row -> new PlanEditorState.ExerciseRow(row.exerciseId(), row.exerciseName(), row.sets(),
                            row.reps(), row.weight()))
                    .toList();
            state = new PlanEditorState(data.planId(), data.name(), rows, null);
        }

        @Override
        public void prepareFailView(String errorMessage) {
            state = new PlanEditorState(null, "", List.of(), errorMessage);
        }
    }
}
