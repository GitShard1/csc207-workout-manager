package use_case.register;

import entity.User;
import use_case.account.AccountEmail;
import use_case.account.AccountPassword;
import use_case.account.AccountProfile;
import use_case.account.AuthSession;
import use_case.account.ProfileDataAccessInterface;
import use_case.account.StartSessionDataAccessInterface;

import java.util.Locale;
import java.util.Optional;

public final class RegisterInteractor implements RegisterInputBoundary {
    private final SignUpDataAccessInterface auth;
    private final ProfileDataAccessInterface profiles;
    private final UsernameAvailabilityDataAccessInterface usernames;
    private final StartSessionDataAccessInterface sessions;
    private final RegisterOutputBoundary presenter;

    public RegisterInteractor(SignUpDataAccessInterface auth, ProfileDataAccessInterface profiles,
                              UsernameAvailabilityDataAccessInterface usernames,
                              StartSessionDataAccessInterface sessions, RegisterOutputBoundary presenter) {
        this.auth = auth;
        this.profiles = profiles;
        this.usernames = usernames;
        this.sessions = sessions;
        this.presenter = presenter;
    }

    @Override
    public void execute(RegisterInputData inputData) {
        final Optional<String> email = AccountEmail.parse(inputData.email());
        final String username = canonicalUsername(inputData.username());
        final String displayName = inputData.displayName() == null ? null : inputData.displayName().strip();
        final String password = inputData.password();

        if (email.isEmpty()) {
            presenter.prepareFailView(RegisterFailure.INVALID_EMAIL);
        } else if (!User.isValidUsername(username)) {
            presenter.prepareFailView(RegisterFailure.INVALID_USERNAME);
        } else if (!User.isValidDisplayName(displayName)) {
            presenter.prepareFailView(RegisterFailure.INVALID_DISPLAY_NAME);
        } else if (!AccountPassword.isLongEnough(password)) {
            presenter.prepareFailView(RegisterFailure.PASSWORD_TOO_SHORT);
        } else if (!password.equals(inputData.passwordConfirmation())) {
            presenter.prepareFailView(RegisterFailure.PASSWORD_MISMATCH);
        } else {
            signUp(email.get(), password, username, displayName);
        }
    }

    /** Usernames are case-insensitive, so they are stored in lowercase. */
    private static String canonicalUsername(String input) {
        return input == null ? null : input.strip().toLowerCase(Locale.ROOT);
    }

    private void signUp(String email, String password, String username, String displayName) {
        switch (auth.signUp(email, password, username, displayName)) {
            case SignUpDataAccessInterface.SignedUp(AuthSession session) -> startSession(session);
            case SignUpDataAccessInterface.Failure.PROFILE_REJECTED -> presenter.prepareFailView(
                    usernames.isTaken(username) ? RegisterFailure.USERNAME_TAKEN : RegisterFailure.RETRYABLE_FAILURE);
            case SignUpDataAccessInterface.Failure.INVALID_EMAIL ->
                    presenter.prepareFailView(RegisterFailure.INVALID_EMAIL);
            case SignUpDataAccessInterface.Failure.PASSWORD_REJECTED ->
                    presenter.prepareFailView(RegisterFailure.PASSWORD_REJECTED);
            case SignUpDataAccessInterface.Failure.ACCOUNT_UNAVAILABLE ->
                    presenter.prepareFailView(RegisterFailure.ACCOUNT_UNAVAILABLE);
            case SignUpDataAccessInterface.Failure.NO_USABLE_SESSION ->
                    presenter.prepareFailView(RegisterFailure.NO_USABLE_SESSION);
            case SignUpDataAccessInterface.Failure.SIGNUP_UNCERTAIN ->
                    presenter.prepareFailView(RegisterFailure.SIGNUP_UNCERTAIN);
            case SignUpDataAccessInterface.Failure.RETRYABLE_FAILURE ->
                    presenter.prepareFailView(RegisterFailure.RETRYABLE_FAILURE);
        }
    }

    private void startSession(AuthSession session) {
        final Optional<User> user = profiles.loadProfile(session);
        if (user.isEmpty()) {
            presenter.prepareFailView(RegisterFailure.ACCOUNT_CREATED_PROFILE_UNAVAILABLE);
            return;
        }
        sessions.start(session, user.get());
        presenter.prepareSuccessView(new RegisterOutputData(AccountProfile.from(user.get())));
    }
}
