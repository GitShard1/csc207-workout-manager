package use_case.register;

import entity.User;

import java.util.Objects;

public interface RegisterAccountGateway {
    /**
     * Called while signed out, serialized with the other account operations. Creates the account and loads its
     * profile using a provisional session. Publish the session and that profile together only on success;
     * discard any provisional credentials and remain signed out on every failure.
     */
    Result register(String email, String username, String displayName, String password);

    sealed interface Result permits Success, Failure { }

    record Success(User user) implements Result {
        public Success {
            Objects.requireNonNull(user);
        }
    }

    enum Failure implements Result {
        USERNAME_TAKEN,
        // A rejected registration for which the UI must not disclose whether the email is registered.
        ACCOUNT_UNAVAILABLE,
        // The request may have created an account, but creation could not be confirmed.
        SIGNUP_UNCERTAIN,
        // The Auth response did not establish a usable session.
        NO_USABLE_SESSION,
        // Service or profile-load failure; no session was published.
        RETRYABLE_FAILURE
    }
}
