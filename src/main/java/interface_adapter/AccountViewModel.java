package interface_adapter;

import java.util.UUID;

/** Holds only the outcome of the current account action until the background worker consumes it. */
public final class AccountViewModel {
    private Outcome outcome;

    public void present(Outcome result) {
        outcome = result;
    }

    public Outcome takeOutcome() {
        final Outcome result = outcome;
        outcome = null;
        return result;
    }

    public sealed interface Outcome permits SignedIn, SignedOut, Error { }

    public record SignedIn(UUID userID, String username, String displayName) implements Outcome { }

    public enum SignedOut implements Outcome {
        SIGNED_OUT
    }

    public record Error(Field field, String message) implements Outcome { }

    public enum Field {
        FORM, EMAIL, USERNAME, DISPLAY_NAME, PASSWORD, CONFIRM_PASSWORD
    }
}
