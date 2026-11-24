package use_case.login;

import entity.User;
import use_case.account.AccountEmail;
import use_case.account.AccountProfile;
import use_case.account.AuthSession;
import use_case.account.ProfileDataAccessInterface;
import use_case.account.StartSessionDataAccessInterface;

import java.util.Optional;

public final class LogInInteractor implements LogInInputBoundary {
    private final SignInDataAccessInterface auth;
    private final ProfileDataAccessInterface profiles;
    private final StartSessionDataAccessInterface sessions;
    private final LogInOutputBoundary presenter;

    public LogInInteractor(SignInDataAccessInterface auth, ProfileDataAccessInterface profiles,
                           StartSessionDataAccessInterface sessions, LogInOutputBoundary presenter) {
        this.auth = auth;
        this.profiles = profiles;
        this.sessions = sessions;
        this.presenter = presenter;
    }

    @Override
    public void execute(LogInInputData inputData) {
        final Optional<String> email = AccountEmail.parse(inputData.email());
        final String password = inputData.password();
        if (email.isEmpty() || password == null || password.isEmpty()) {
            presenter.prepareFailView(LogInFailure.BAD_CREDENTIALS);
            return;
        }
        switch (auth.signIn(email.get(), password)) {
            case SignInDataAccessInterface.SignedIn(AuthSession session) -> startSession(session);
            case SignInDataAccessInterface.Failure.BAD_CREDENTIALS ->
                    presenter.prepareFailView(LogInFailure.BAD_CREDENTIALS);
            case SignInDataAccessInterface.Failure.NO_USABLE_SESSION ->
                    presenter.prepareFailView(LogInFailure.NO_USABLE_SESSION);
            case SignInDataAccessInterface.Failure.RETRYABLE_FAILURE ->
                    presenter.prepareFailView(LogInFailure.RETRYABLE_FAILURE);
        }
    }

    // The user only counts as signed in once their profile has loaded, so a failed load leaves them signed out.
    private void startSession(AuthSession session) {
        final Optional<User> user = profiles.loadProfile(session);
        if (user.isEmpty()) {
            presenter.prepareFailView(LogInFailure.RETRYABLE_FAILURE);
            return;
        }
        sessions.start(session, user.get());
        presenter.prepareSuccessView(new LogInOutputData(AccountProfile.from(user.get())));
    }
}
