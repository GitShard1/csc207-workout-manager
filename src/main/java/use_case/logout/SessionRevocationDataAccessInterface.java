package use_case.logout;

import use_case.account.AuthSession;

public interface SessionRevocationDataAccessInterface {
    /** Asks the account service to invalidate the session. Best effort: failures are not reported. */
    void revoke(AuthSession session);
}
