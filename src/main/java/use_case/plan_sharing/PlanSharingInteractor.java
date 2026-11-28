package use_case.plan_sharing;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import static use_case.plan_sharing.PlanSharingException.Reason.INVALID_CODE;
import static use_case.plan_sharing.PlanSharingException.Reason.INVALID_REQUEST;
import static use_case.plan_sharing.PlanSharingException.Reason.SIGN_IN_REQUIRED;

public final class PlanSharingInteractor {
    private static final Pattern CODE_FORMAT =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    private static final Pattern REVISION_FORMAT = Pattern.compile("[0-9a-f]{64}");
    private final PlanSharingGateway plans;

    public PlanSharingInteractor(PlanSharingGateway plans) {
        this.plans = plans;
    }

    public List<PlanSharingGateway.PlanSummary> list(UUID viewer) throws PlanSharingException {
        return plans.list(requireViewer(viewer));
    }

    public PlanSharingGateway.GeneratedCode generate(UUID viewer, UUID planId) throws PlanSharingException {
        return plans.generate(requireViewer(viewer), requireId(planId));
    }

    public void revoke(UUID viewer, UUID planId) throws PlanSharingException {
        plans.revoke(requireViewer(viewer), requireId(planId));
    }

    public PlanSharingGateway.Preview preview(UUID viewer, String rawCode) throws PlanSharingException {
        return plans.preview(requireViewer(viewer), normalizeCode(rawCode));
    }

    public PlanSharingGateway.CopyResult copy(UUID viewer, String rawCode, String revision, UUID requestId)
            throws PlanSharingException {
        requireViewer(viewer);
        final String code = normalizeCode(rawCode);
        if (revision == null || !REVISION_FORMAT.matcher(revision).matches()) {
            throw new PlanSharingException(INVALID_REQUEST);
        }
        return plans.copy(viewer, code, revision, requireId(requestId));
    }

    private static UUID requireViewer(UUID viewer) throws PlanSharingException {
        if (viewer == null) {
            throw new PlanSharingException(SIGN_IN_REQUIRED);
        }
        return viewer;
    }

    private static UUID requireId(UUID id) throws PlanSharingException {
        if (id == null) {
            throw new PlanSharingException(INVALID_REQUEST);
        }
        return id;
    }

    private static String normalizeCode(String rawCode) throws PlanSharingException {
        final String code = rawCode == null ? "" : rawCode.strip().toLowerCase(Locale.ROOT);
        if (!CODE_FORMAT.matcher(code).matches()) {
            throw new PlanSharingException(INVALID_CODE);
        }
        return code;
    }
}
