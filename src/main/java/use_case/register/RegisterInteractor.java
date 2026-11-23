package use_case.register;

import use_case.account.AccountEmail;
import use_case.account.AccountProfile;
import use_case.account.AccountProfileRules;

import java.util.Locale;
import java.util.Optional;

public final class RegisterInteractor implements RegisterInputBoundary {
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
        final String username = strip(request.username());
        final String displayName = strip(request.displayName());
        final String password = request.password();

        if (email.isEmpty()) {
            output.present(RegisterResponse.Failure.INVALID_EMAIL);
        } else if (!AccountProfileRules.isUsername(username)) {
            output.present(RegisterResponse.Failure.INVALID_USERNAME);
        } else if (!AccountProfileRules.hasDisplayName(displayName)) {
            output.present(RegisterResponse.Failure.INVALID_DISPLAY_NAME);
        } else if (password == null || password.codePointCount(0, password.length()) < MIN_PASSWORD_LENGTH) {
            output.present(RegisterResponse.Failure.PASSWORD_TOO_SHORT);
        } else if (!password.equals(request.passwordConfirmation())) {
            output.present(RegisterResponse.Failure.PASSWORD_MISMATCH);
        } else {
            output.present(toResponse(gateway.register(
                    email.get(), username.toLowerCase(Locale.ROOT), displayName, password)));
        }
    }

    private static String strip(String text) {
        return text == null ? null : text.strip();
    }

    private static RegisterResponse toResponse(RegisterAccountGateway.Result result) {
        return switch (result) {
            case RegisterAccountGateway.Success success ->
                    new RegisterResponse.Success(AccountProfile.from(success.user()));
            case RegisterAccountGateway.Failure failure -> switch (failure) {
                case INVALID_EMAIL -> RegisterResponse.Failure.INVALID_EMAIL;
                case PASSWORD_REJECTED -> RegisterResponse.Failure.PASSWORD_REJECTED;
                case USERNAME_TAKEN -> RegisterResponse.Failure.USERNAME_TAKEN;
                case ACCOUNT_UNAVAILABLE -> RegisterResponse.Failure.ACCOUNT_UNAVAILABLE;
                case SIGNUP_UNCERTAIN -> RegisterResponse.Failure.SIGNUP_UNCERTAIN;
                case NO_USABLE_SESSION -> RegisterResponse.Failure.NO_USABLE_SESSION;
                case ACCOUNT_CREATED_PROFILE_UNAVAILABLE ->
                        RegisterResponse.Failure.ACCOUNT_CREATED_PROFILE_UNAVAILABLE;
                case RETRYABLE_FAILURE -> RegisterResponse.Failure.RETRYABLE_FAILURE;
            };
        };
    }
}
