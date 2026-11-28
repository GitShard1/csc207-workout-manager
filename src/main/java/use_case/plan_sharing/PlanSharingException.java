package use_case.plan_sharing;

/** A failure that can be shown without exposing private plan details or the share code. */
public final class PlanSharingException extends Exception {
    public enum Reason {
        SIGN_IN_REQUIRED, INVALID_CODE, UNAVAILABLE, STALE, DELETED, REQUEST_FAILED, INVALID_REQUEST
    }

    private final Reason reason;

    public PlanSharingException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
