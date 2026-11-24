package use_case.logout;

import use_case.account.AuthSession;

import java.util.Optional;

public final class LogOutInteractor implements LogOutInputBoundary {
    private final LogOutSessionDataAccessInterface sessions;
    private final SessionRevocationDataAccessInterface auth;
    private final LogOutOutputBoundary presenter;

    public LogOutInteractor(LogOutSessionDataAccessInterface sessions, SessionRevocationDataAccessInterface auth,
                            LogOutOutputBoundary presenter) {
        this.sessions = sessions;
        this.auth = auth;
        this.presenter = presenter;
    }

    /**
     * Signs out locally before contacting the account service, so the user is signed out even if revocation fails
     * or is slow.
     */
    @Override
    public void execute() {
        final Optional<AuthSession> ended = sessions.endCurrentSession();
        presenter.prepareSuccessView();
        ended.ifPresent(auth::revoke);
    }
}
