package interface_adapter.register;

import interface_adapter.AccountViewModel;
import interface_adapter.AccountViewModel.Error;
import interface_adapter.AccountViewModel.Field;
import interface_adapter.AccountViewModel.SignedIn;
import use_case.register.RegisterOutputBoundary;
import use_case.register.RegisterResponse;

public final class RegisterPresenter implements RegisterOutputBoundary {
    private final AccountViewModel model;

    public RegisterPresenter(AccountViewModel model) {
        this.model = model;
    }

    @Override
    public void present(RegisterResponse response) {
        model.present(switch (response) {
            case RegisterResponse.Success success -> {
                final var profile = success.profile();
                yield new SignedIn(profile.userID(), profile.username(), profile.displayName());
            }
            case RegisterResponse.Failure failure -> switch (failure) {
                case INVALID_EMAIL -> new Error(Field.EMAIL, "Enter a valid email address.");
                case INVALID_USERNAME -> new Error(Field.USERNAME, "Use 3–24 letters (A–Z), digits or underscores.");
                case INVALID_DISPLAY_NAME -> new Error(Field.DISPLAY_NAME, "Enter a display name.");
                case PASSWORD_TOO_SHORT -> new Error(Field.PASSWORD, "Use at least 8 characters.");
                case PASSWORD_REJECTED -> new Error(Field.PASSWORD, "Choose a different password.");
                case PASSWORD_MISMATCH -> new Error(Field.CONFIRM_PASSWORD, "Passwords don't match.");
                case USERNAME_TAKEN -> new Error(Field.USERNAME, "That username is taken.");
                case ACCOUNT_UNAVAILABLE -> new Error(Field.FORM,
                        "We couldn't register with those details. Check them or try logging in.");
                case SIGNUP_UNCERTAIN -> new Error(Field.FORM,
                        "We couldn't confirm registration. Try logging in before registering again.");
                case NO_USABLE_SESSION -> new Error(Field.FORM,
                        "Registration didn't log you in. If asked to confirm your email, do that before logging in.");
                case RETRYABLE_FAILURE -> new Error(Field.FORM,
                        "We couldn't finish registration. Try logging in first, or try again later.");
            };
        });
    }
}
