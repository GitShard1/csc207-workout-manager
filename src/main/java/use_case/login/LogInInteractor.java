package use_case.login;

import use_case.account.AccountEmail;
import use_case.account.AccountProfile;

import java.util.Optional;

public final class LogInInteractor implements LogInInputBoundary {
    private final LogInAccountGateway gateway;
    private final LogInOutputBoundary output;

    public LogInInteractor(LogInAccountGateway gateway, LogInOutputBoundary output) {
        this.gateway = gateway;
        this.output = output;
    }

    @Override
    public void execute(LogInRequest request) {
        final Optional<String> email = AccountEmail.parse(request.email());
        final String password = request.password();
        if (email.isEmpty() || password == null || password.isEmpty()) {
            output.present(LogInResponse.Failure.BAD_CREDENTIALS);
        } else {
            output.present(toResponse(gateway.logIn(email.get(), password)));
        }
    }

    private static LogInResponse toResponse(LogInAccountGateway.Result result) {
        return switch (result) {
            case LogInAccountGateway.Success success -> new LogInResponse.Success(AccountProfile.from(success.user()));
            case LogInAccountGateway.Failure failure -> switch (failure) {
                case BAD_CREDENTIALS -> LogInResponse.Failure.BAD_CREDENTIALS;
                case NO_USABLE_SESSION -> LogInResponse.Failure.NO_USABLE_SESSION;
                case RETRYABLE_FAILURE -> LogInResponse.Failure.RETRYABLE_FAILURE;
            };
        };
    }
}
