package use_case.login;

import entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import use_case.account.AccountProfile;
import use_case.account.AuthSession;
import use_case.account.ProfileDataAccessInterface;
import use_case.account.StartSessionDataAccessInterface;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class LogInInteractorTest {
    private static final UUID ID = UUID.randomUUID();
    private static final AuthSession SESSION = new AuthSession(ID, "token");
    private static final LogInInputData VALID = new LogInInputData("  Person@example.com  ", " p ");

    private final FakeAccounts accounts = new FakeAccounts();
    private final List<Object> responses = new ArrayList<>();
    private final LogInOutputBoundary presenter = new LogInOutputBoundary() {
        @Override
        public void prepareSuccessView(LogInOutputData outputData) {
            responses.add(outputData);
        }

        @Override
        public void prepareFailView(LogInFailure failure) {
            responses.add(failure);
        }
    };
    private final LogInInputBoundary interactor = new LogInInteractor(accounts, accounts, accounts, presenter);

    @Test
    void signsInWithTheLoadedProfileAndPresentsASnapshot() {
        final User user = new User(ID, "new_user", "New User");
        accounts.signIn = new SignInDataAccessInterface.SignedIn(SESSION);
        accounts.profile = Optional.of(user);

        interactor.execute(VALID);

        assertEquals("Person@example.com", accounts.email);
        assertEquals(VALID.password(), accounts.password);
        assertEquals(SESSION, accounts.profileRequestedFor);
        assertEquals(SESSION, accounts.startedSession);
        assertEquals(user, accounts.startedUser);
        user.setDisplayName("Changed After Login");
        assertEquals(List.of(new LogInOutputData(new AccountProfile(ID, "new_user", "New User"))), responses);
    }

    @Test
    void profileThatCannotLoadLeavesTheUserSignedOut() {
        accounts.signIn = new SignInDataAccessInterface.SignedIn(SESSION);
        accounts.profile = Optional.empty();

        interactor.execute(VALID);

        assertNull(accounts.startedSession);
        assertEquals(List.of(LogInFailure.RETRYABLE_FAILURE), responses);
    }

    @ParameterizedTest
    @CsvSource({"BAD_CREDENTIALS, BAD_CREDENTIALS", "NO_USABLE_SESSION, NO_USABLE_SESSION",
        "RETRYABLE_FAILURE, RETRYABLE_FAILURE"})
    void presentsSignInFailureWithoutLoadingAProfile(SignInDataAccessInterface.Failure failure, LogInFailure expected) {
        accounts.signIn = failure;

        interactor.execute(VALID);

        assertEquals(List.of(expected), responses);
        assertNull(accounts.profileRequestedFor);
        assertNull(accounts.startedSession);
    }

    @Test
    void malformedEmailAndEmptyPasswordDoNotContactTheAccountService() {
        interactor.execute(new LogInInputData("not-an-email", "any_password"));
        interactor.execute(new LogInInputData("known@example.com", ""));

        assertEquals(List.of(LogInFailure.BAD_CREDENTIALS, LogInFailure.BAD_CREDENTIALS), responses);
        assertEquals(0, accounts.signInCalls);
    }

    @Test
    void diagnosticStringOmitsSubmittedCredentials() {
        final LogInInputData request = new LogInInputData("person@example.com", "password123");
        assertFalse(request.toString().contains(request.email()));
        assertFalse(request.toString().contains(request.password()));
    }

    private static final class FakeAccounts
            implements SignInDataAccessInterface, ProfileDataAccessInterface, StartSessionDataAccessInterface {
        private SignInDataAccessInterface.Result signIn;
        private Optional<User> profile = Optional.empty();
        private int signInCalls;
        private String email;
        private String password;
        private AuthSession profileRequestedFor;
        private AuthSession startedSession;
        private User startedUser;

        @Override
        public SignInDataAccessInterface.Result signIn(String email, String password) {
            signInCalls++;
            this.email = email;
            this.password = password;
            return signIn;
        }

        @Override
        public Optional<User> loadProfile(AuthSession session) {
            profileRequestedFor = session;
            return profile;
        }

        @Override
        public void start(AuthSession session, User user) {
            startedSession = session;
            startedUser = user;
        }
    }
}
