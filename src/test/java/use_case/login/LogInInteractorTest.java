package use_case.login;

import entity.User;
import org.junit.jupiter.api.Test;
import use_case.account.AccountProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class LogInInteractorTest {
    private static final LogInRequest VALID = new LogInRequest("  Person@example.com  ", " p ");

    private final LoginFake gateway = new LoginFake();
    private final List<LogInResponse> responses = new ArrayList<>();
    private final LogInInputBoundary interactor = new LogInInteractor(gateway, responses::add);

    @Test
    void noSessionAndProfileFailureLeaveSignedOutThenSuccessfulRetryPublishesProfile() {
        gateway.result = LogInAccountGateway.Failure.NO_USABLE_SESSION;
        interactor.execute(VALID);
        assertNull(gateway.signedIn);

        gateway.result = LogInAccountGateway.Failure.RETRYABLE_FAILURE;
        interactor.execute(VALID);
        assertNull(gateway.signedIn);

        final User profile = new User(UUID.randomUUID(), "new_user", "New User");
        gateway.result = new LogInAccountGateway.Success(profile);
        interactor.execute(VALID);

        assertSame(profile, gateway.signedIn);
        assertEquals("Person@example.com", gateway.email);
        assertEquals(VALID.password(), gateway.password);
        profile.setDisplayName("Changed After Login");
        assertEquals(List.of(LogInResponse.Failure.NO_USABLE_SESSION, LogInResponse.Failure.RETRYABLE_FAILURE,
                new LogInResponse.Success(new AccountProfile(profile.getUserID(), "new_user", "New User"))), responses);
    }

    @Test
    void unknownEmailAndWrongPasswordHaveTheSameOutcome() {
        gateway.result = LogInAccountGateway.Failure.BAD_CREDENTIALS;

        interactor.execute(new LogInRequest("missing@example.com", "any_password"));
        interactor.execute(new LogInRequest("known@example.com", "wrong_password"));

        assertEquals(List.of(LogInResponse.Failure.BAD_CREDENTIALS, LogInResponse.Failure.BAD_CREDENTIALS), responses);
        assertEquals(2, gateway.calls);
        assertNull(gateway.signedIn);
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
        private User signedIn;

        @Override
        public Result logIn(String email, String password) {
            calls++;
            this.email = email;
            this.password = password;
            if (result instanceof Success success) {
                signedIn = success.user();
            }
            return result;
        }
    }
}
