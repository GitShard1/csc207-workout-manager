package use_case.register;

import entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import use_case.account.AccountProfile;
import use_case.account.AuthSession;
import use_case.account.ProfileDataAccessInterface;
import use_case.account.StartSessionDataAccessInterface;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class RegisterInteractorTest {
    private static final UUID ID = UUID.randomUUID();
    private static final AuthSession SESSION = new AuthSession(ID, "token");
    private static final RegisterInputData VALID =
            new RegisterInputData("person@example.com", "new_user", "New User", "abcdefgh", "abcdefgh");

    private final FakeAccounts accounts = new FakeAccounts();
    private final List<Object> responses = new ArrayList<>();
    private final RegisterOutputBoundary presenter = new RegisterOutputBoundary() {
        @Override
        public void prepareSuccessView(RegisterOutputData outputData) {
            responses.add(outputData);
        }

        @Override
        public void prepareFailView(RegisterFailure failure) {
            responses.add(failure);
        }
    };
    private final RegisterInputBoundary interactor =
            new RegisterInteractor(accounts, accounts, accounts, accounts, presenter);

    @Test
    void normalizesProfileInputAndSignsInWithThePersistedProfile() {
        final String password = " 123456 ";
        final User persisted = new User(ID, "new_user", "Persisted User");
        accounts.signUp = new SignUpDataAccessInterface.SignedUp(SESSION);
        accounts.profile = Optional.of(persisted);

        interactor.execute(new RegisterInputData("  Person+test@example.com ", "  NEW_User  ", " New User ",
                password, password));

        assertEquals("Person+test@example.com", accounts.email);
        assertEquals("new_user", accounts.username);
        assertEquals("New User", accounts.displayName);
        assertEquals(password, accounts.password);
        assertEquals(SESSION, accounts.startedSession);
        assertEquals(persisted, accounts.startedUser);
        assertEquals(List.of(new RegisterOutputData(new AccountProfile(ID, "new_user", "Persisted User"))),
                responses);
    }

    @Test
    void createdAccountWhoseProfileCannotLoadStaysSignedOut() {
        accounts.signUp = new SignUpDataAccessInterface.SignedUp(SESSION);

        interactor.execute(VALID);

        assertNull(accounts.startedSession);
        assertEquals(List.of(RegisterFailure.ACCOUNT_CREATED_PROFILE_UNAVAILABLE), responses);
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectsInvalidFieldsWithoutContactingTheAccountService(RegisterInputData request,
                                                                 RegisterFailure expected) {
        interactor.execute(request);

        assertEquals(List.of(expected), responses);
        assertEquals(0, accounts.signUpCalls);
    }

    private static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of(new RegisterInputData("missing-at.example.com", "new_user", "New User", "abcdefgh",
                        "abcdefgh"), RegisterFailure.INVALID_EMAIL),
                Arguments.of(new RegisterInputData("a..b@example.com", "new_user", "New User", "abcdefgh", "abcdefgh"),
                        RegisterFailure.INVALID_EMAIL),
                Arguments.of(new RegisterInputData("person@example.com", "ab", "New User", "abcdefgh", "abcdefgh"),
                        RegisterFailure.INVALID_USERNAME),
                Arguments.of(new RegisterInputData("person@example.com", "new-user", "New User", "abcdefgh",
                        "abcdefgh"), RegisterFailure.INVALID_USERNAME),
                Arguments.of(new RegisterInputData("person@example.com", "new_user", " ", "abcdefgh", "abcdefgh"),
                        RegisterFailure.INVALID_DISPLAY_NAME),
                Arguments.of(new RegisterInputData("person@example.com", "new_user", "New User", "1234567", "1234567"),
                        RegisterFailure.PASSWORD_TOO_SHORT),
                Arguments.of(new RegisterInputData("person@example.com", "new_user", "New User", " abcdefgh",
                        "abcdefgh"), RegisterFailure.PASSWORD_MISMATCH));
    }

    @ParameterizedTest
    @CsvSource({"true, USERNAME_TAKEN", "false, RETRYABLE_FAILURE"})
    void rejectedProfileIsReportedAsTakenOnlyWhenTheUsernameIsConfirmedTaken(
            boolean taken, RegisterFailure expected) {
        accounts.signUp = SignUpDataAccessInterface.Failure.PROFILE_REJECTED;
        accounts.usernameTaken = taken;

        interactor.execute(VALID);

        assertEquals("new_user", accounts.checkedUsername);
        assertEquals(List.of(expected), responses);
    }

    @ParameterizedTest
    @CsvSource({
        "INVALID_EMAIL, INVALID_EMAIL",
        "PASSWORD_REJECTED, PASSWORD_REJECTED",
        "ACCOUNT_UNAVAILABLE, ACCOUNT_UNAVAILABLE",
        "NO_USABLE_SESSION, NO_USABLE_SESSION",
        "SIGNUP_UNCERTAIN, SIGNUP_UNCERTAIN",
        "RETRYABLE_FAILURE, RETRYABLE_FAILURE"
    })
    void presentsOtherSignUpFailuresWithoutFurtherRequests(SignUpDataAccessInterface.Failure failure,
                                                           RegisterFailure expected) {
        accounts.signUp = failure;

        interactor.execute(VALID);

        assertEquals(List.of(expected), responses);
        assertNull(accounts.checkedUsername);
        assertNull(accounts.startedSession);
    }

    @Test
    void diagnosticStringOmitsSubmittedCredentials() {
        final RegisterInputData request =
                new RegisterInputData("person@example.com", "new_user", "New User", "abcdefgh", "confirmation");
        assertFalse(request.toString().contains(request.email()));
        assertFalse(request.toString().contains(request.password()));
        assertFalse(request.toString().contains(request.passwordConfirmation()));
    }

    private static final class FakeAccounts
            implements SignUpDataAccessInterface, ProfileDataAccessInterface, UsernameAvailabilityDataAccessInterface,
            StartSessionDataAccessInterface {
        private SignUpDataAccessInterface.Result signUp;
        private Optional<User> profile = Optional.empty();
        private boolean usernameTaken;
        private int signUpCalls;
        private String email;
        private String password;
        private String username;
        private String displayName;
        private String checkedUsername;
        private AuthSession startedSession;
        private User startedUser;

        @Override
        public SignUpDataAccessInterface.Result signUp(String email, String password, String username,
                                                       String displayName) {
            signUpCalls++;
            this.email = email;
            this.password = password;
            this.username = username;
            this.displayName = displayName;
            return signUp;
        }

        @Override
        public Optional<User> loadProfile(AuthSession session) {
            return profile;
        }

        @Override
        public boolean isTaken(String candidate) {
            checkedUsername = candidate;
            return usernameTaken;
        }

        @Override
        public void start(AuthSession session, User user) {
            startedSession = session;
            startedUser = user;
        }
    }
}
