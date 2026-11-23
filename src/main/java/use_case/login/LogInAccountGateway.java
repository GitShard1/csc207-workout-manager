package use_case.login;

import entity.User;

import java.util.Objects;

public interface LogInAccountGateway {
    /**
     * Called while signed out, serialized with the other account operations. Authenticates and loads the matching
     * user's profile using a provisional session. Publish the session and that profile together only on success;
     * discard any provisional credentials and remain signed out on every failure.
     */
    Result logIn(String email, String password);

    sealed interface Result permits Success, Failure { }

    record Success(User user) implements Result {
        public Success {
            Objects.requireNonNull(user);
        }
    }

    enum Failure implements Result {
        // Unknown email or incorrect password; intentionally indistinguishable.
        BAD_CREDENTIALS,
        NO_USABLE_SESSION,
        // Service or profile-load failure; no session was published.
        RETRYABLE_FAILURE
    }
}
