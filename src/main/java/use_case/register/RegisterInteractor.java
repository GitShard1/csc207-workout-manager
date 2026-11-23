package use_case.register;

import use_case.account.AccountEmail;
import use_case.account.AccountProfile;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class RegisterInteractor implements RegisterInputBoundary {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,24}");
    private static final Pattern BLANK_DISPLAY_NAME =
            Pattern.compile("[\\s\\p{javaWhitespace}]*", Pattern.UNICODE_CHARACTER_CLASS);
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final RegisterAccountGateway gateway;
    private final RegisterOutputBoundary output;

    public RegisterInteractor(RegisterAccountGateway gateway, RegisterOutputBoundary output) {
        this.gateway = gateway;
        this.output = output;
    }

    @Override
    public void execute(RegisterRequest request) {
        final Optional<String> email = AccountEmail.parse(request.email());
        final String username = request.username() == null ? "" : request.username().strip();
        final String displayName = request.displayName() == null ? "" : request.displayName().strip();
        final String password = request.password();

        if (email.isEmpty()) {
            output.present(RegisterResponse.Failure.INVALID_EMAIL);
        } else if (!USERNAME.matcher(username).matches()) {
            output.present(RegisterResponse.Failure.INVALID_USERNAME);
        } else if (BLANK_DISPLAY_NAME.matcher(displayName).matches()) {
            output.present(RegisterResponse.Failure.INVALID_DISPLAY_NAME);
        } else if (password == null || password.codePointCount(0, password.length()) < MIN_PASSWORD_LENGTH) {
            output.present(RegisterResponse.Failure.PASSWORD_TOO_SHORT);
        } else if (!password.equals(request.passwordConfirmation())) {
            output.present(RegisterResponse.Failure.PASSWORD_MISMATCH);
        } else {
            final RegisterAccountGateway.Result result = gateway.register(
                    email.get(), username.toLowerCase(Locale.ROOT), displayName, password);
            output.present(toResponse(result));
        }
    }

    private static RegisterResponse toResponse(RegisterAccountGateway.Result result) {
        return switch (result) {
            case RegisterAccountGateway.Success success ->
                    new RegisterResponse.Success(AccountProfile.from(success.user()));
            case RegisterAccountGateway.Failure failure -> switch (failure) {
                case USERNAME_TAKEN -> RegisterResponse.Failure.USERNAME_TAKEN;
                case ACCOUNT_UNAVAILABLE -> RegisterResponse.Failure.ACCOUNT_UNAVAILABLE;
                case SIGNUP_UNCERTAIN -> RegisterResponse.Failure.SIGNUP_UNCERTAIN;
                case NO_USABLE_SESSION -> RegisterResponse.Failure.NO_USABLE_SESSION;
                case RETRYABLE_FAILURE -> RegisterResponse.Failure.RETRYABLE_FAILURE;
            };
        };
    }
}
