package use_case.plan_sharing;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Account-bound plan sharing operations performed by the plan store. */
public interface PlanSharingGateway {
    record PlanSummary(UUID id, String name, Instant shareExpiresAt) { }

    record GeneratedCode(String code, Instant expiresAt) { }

    record Preview(String name, List<ExerciseTarget> exercises, String revision) {
        public Preview {
            exercises = List.copyOf(exercises);
        }
    }

    record ExerciseTarget(String name, int sets, int reps, Double weight) { }

    record CopyResult(UUID id, String name) { }

    List<PlanSummary> list(UUID viewer) throws PlanSharingException;

    GeneratedCode generate(UUID viewer, UUID planId) throws PlanSharingException;

    void revoke(UUID viewer, UUID planId) throws PlanSharingException;

    Preview preview(UUID viewer, String code) throws PlanSharingException;

    CopyResult copy(UUID viewer, String code, String revision, UUID requestId) throws PlanSharingException;
}
