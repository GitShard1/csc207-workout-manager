package use_case.register;

import entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import use_case.account.AccountProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RegisterInteractorTest {
    private static final RegisterRequest VALID =
            new RegisterRequest("person@example.com", "new_user", "New User", "abcdefgh", "abcdefgh");

    private final RegisterFake gateway = new RegisterFake();
    private final List<RegisterResponse> responses = new ArrayList<>();
    private final RegisterInputBoundary interactor = new RegisterInteractor(gateway, responses::add);

    @Test
    void normalizesProfileInputWithoutChangingThePasswordAndReturnsPersistedProfile() {
        final String password = " 123456 ";
        final User profile = new User(UUID.randomUUID(), "new_user", "Persisted User");
        gateway.result = new RegisterAccountGateway.Success(profile);

        interactor.execute(new RegisterRequest("  Person+test@example.com ", "  NEW_User  ", " New User ",
                password, password));

        assertEquals("Person+test@example.com", gateway.email);
        assertEquals("new_user", gateway.username);
        assertEquals("New User", gateway.displayName);
        assertEquals(password, gateway.password);
        assertEquals(List.of(new RegisterResponse.Success(
                new AccountProfile(profile.getUserID(), "new_user", "Persisted User"))), responses);
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectsInvalidFieldsWithoutCallingGateway(RegisterRequest request, RegisterResponse.Failure expected) {
        interactor.execute(request);

        assertEquals(List.of(expected), responses);
        assertEquals(0, gateway.calls);
    }

    private static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of(new RegisterRequest("missing-at.example.com", "new_user", "New User", "abcdefgh",
                        "abcdefgh"), RegisterResponse.Failure.INVALID_EMAIL),
                Arguments.of(new RegisterRequest("a..b@example.com", "new_user", "New User", "abcdefgh", "abcdefgh"),
                        RegisterResponse.Failure.INVALID_EMAIL),
                Arguments.of(new RegisterRequest("person@example.com", "ab", "New User", "abcdefgh", "abcdefgh"),
                        RegisterResponse.Failure.INVALID_USERNAME),
                Arguments.of(new RegisterRequest("person@example.com", "a".repeat(25), "New User", "abcdefgh",
                        "abcdefgh"), RegisterResponse.Failure.INVALID_USERNAME),
                Arguments.of(new RegisterRequest("person@example.com", "\u212Aelvin", "New User", "abcdefgh",
                        "abcdefgh"), RegisterResponse.Failure.INVALID_USERNAME),
                Arguments.of(new RegisterRequest("person@example.com", "new-user", "New User", "abcdefgh",
                        "abcdefgh"), RegisterResponse.Failure.INVALID_USERNAME),
                Arguments.of(new RegisterRequest("person@example.com", "new_user", " \n ", "abcdefgh", "abcdefgh"),
                        RegisterResponse.Failure.INVALID_DISPLAY_NAME),
                Arguments.of(new RegisterRequest("person@example.com", "new_user", "\u00A0", "abcdefgh", "abcdefgh"),
                        RegisterResponse.Failure.INVALID_DISPLAY_NAME),
                Arguments.of(new RegisterRequest("person@example.com", "new_user", "\t\u202F\u0085", "abcdefgh",
                        "abcdefgh"), RegisterResponse.Failure.INVALID_DISPLAY_NAME),
                Arguments.of(new RegisterRequest("person@example.com", "new_user", "New User", "1234567", "1234567"),
                        RegisterResponse.Failure.PASSWORD_TOO_SHORT),
                Arguments.of(new RegisterRequest("person@example.com", "new_user", "New User", " abcdefgh", "abcdefgh"),
                        RegisterResponse.Failure.PASSWORD_MISMATCH));
    }

    @ParameterizedTest
    @CsvSource({
        "INVALID_EMAIL, INVALID_EMAIL",
        "PASSWORD_REJECTED, PASSWORD_REJECTED",
        "USERNAME_TAKEN, USERNAME_TAKEN",
        "ACCOUNT_UNAVAILABLE, ACCOUNT_UNAVAILABLE",
        "SIGNUP_UNCERTAIN, SIGNUP_UNCERTAIN",
        "NO_USABLE_SESSION, NO_USABLE_SESSION",
        "ACCOUNT_CREATED_PROFILE_UNAVAILABLE, ACCOUNT_CREATED_PROFILE_UNAVAILABLE",
        "RETRYABLE_FAILURE, RETRYABLE_FAILURE"
    })
    void presentsGatewayFailure(RegisterAccountGateway.Failure result, RegisterResponse.Failure expected) {
        gateway.result = result;

        interactor.execute(VALID);

        assertEquals(List.of(expected), responses);
        assertEquals(1, gateway.calls);
    }

    @Test
    void diagnosticStringOmitsSubmittedCredentials() {
        assertFalse(VALID.toString().contains(VALID.email()));
        assertFalse(VALID.toString().contains(VALID.password()));
        assertFalse(new RegisterRequest("person@example.com", "new_user", "New User", "abcdefgh",
                "secret-confirmation").toString().contains("secret-confirmation"));
    }

    private static final class RegisterFake implements RegisterAccountGateway {
        private Result result;
        private int calls;
        private String email;
        private String username;
        private String displayName;
        private String password;
        @Override
        public Result register(String email, String username, String displayName, String password) {
            calls++;
            this.email = email;
            this.username = username;
            this.displayName = displayName;
            this.password = password;
            return result;
        }
    }
}
