package interface_adapter.plan_sharing;

import use_case.plan_sharing.PlanSharingException;
import use_case.plan_sharing.PlanSharingGateway;
import use_case.plan_sharing.PlanSharingInteractor;

import java.util.List;
import java.util.UUID;

/** Returns request-local results for the Swing caller to publish on the event dispatch thread. */
public final class PlanSharingController {
    private final PlanSharingInteractor sharing;

    public PlanSharingController(PlanSharingInteractor sharing) {
        this.sharing = sharing;
    }

    public List<PlanSharingGateway.PlanSummary> list(UUID viewer) throws PlanSharingException {
        return sharing.list(viewer);
    }

    public PlanSharingGateway.GeneratedCode generate(UUID viewer, UUID planId) throws PlanSharingException {
        return sharing.generate(viewer, planId);
    }

    public void revoke(UUID viewer, UUID planId) throws PlanSharingException {
        sharing.revoke(viewer, planId);
    }

    public PlanSharingGateway.Preview preview(UUID viewer, String code) throws PlanSharingException {
        return sharing.preview(viewer, code);
    }

    public PlanSharingGateway.CopyResult copy(UUID viewer, String code, String revision, UUID requestId)
            throws PlanSharingException {
        return sharing.copy(viewer, code, revision, requestId);
    }
}
