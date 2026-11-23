package use_case.login;

import entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import use_case.account.AccountProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LogInInteractorTest {
    private static final LogInRequest VALID = new LogInRequest("  Person@example.com  ", " p ");

    private final LoginFake gateway = new LoginFake();
    private final List<LogInResponse> responses = new ArrayList<>();
    private final LogInInputBoundary interactor = new LogInInteractor(gateway, responses::add);

    @Test
    void forwardsNormalizedCredentialsAndReturnsAProfileSnapshot() {
        final User profile = new User(UUID.randomUUID(), "new_user", "New User");
        gateway.result = new LogInAccountGateway.Success(profile);
        interactor.execute(VALID);

        assertEquals("Person@example.com", gateway.email);
        assertEquals(VALID.password(), gateway.password);
        profile.setDisplayName("Changed After Login");
        assertEquals(List.of(new LogInResponse.Success(
                new AccountProfile(profile.getUserID(), "new_user", "New User"))), responses);
    }

    @ParameterizedTest
    @CsvSource({"BAD_CREDENTIALS, BAD_CREDENTIALS", "NO_USABLE_SESSION, NO_USABLE_SESSION",
        "RETRYABLE_FAILURE, RETRYABLE_FAILURE"})
    void presentsGatewayFailure(LogInAccountGateway.Failure result, LogInResponse.Failure expected) {
        gateway.result = result;

        interactor.execute(VALID);

        assertEquals(List.of(expected), responses);
    }

    @Test
    void malformedEmailAndEmptyPasswordDoNotCallTheGateway() {
        interactor.execute(new LogInRequest("not-an-email", "any_password"));
        interactor.execute(new LogInRequest("known@example.com", ""));

        assertEquals(List.of(LogInResponse.Failure.BAD_CREDENTIALS, LogInResponse.Failure.BAD_CREDENTIALS), responses);
        assertEquals(0, gateway.calls);
    }

    @Test
    void diagnosticStringOmitsSubmittedCredentials() {
        final LogInRequest request = new LogInRequest("person@example.com", "password123");
        assertFalse(request.toString().contains(request.email()));
        assertFalse(request.toString().contains(request.password()));
    }

    private static final class LoginFake implements LogInAccountGateway {
        private Result result;
        private int calls;
        private String email;
        private String password;
        @Override
        public Result logIn(String email, String password) {
            calls++;
            this.email = email;
            this.password = password;
            return result;
        }
    }
}
