package interface_adapter.login;

import interface_adapter.AccountViewModel;
import interface_adapter.AccountViewModel.Error;
import interface_adapter.AccountViewModel.Field;
import interface_adapter.AccountViewModel.SignedIn;
import use_case.login.LogInOutputBoundary;
import use_case.login.LogInResponse;

public final class LogInPresenter implements LogInOutputBoundary {
    private final AccountViewModel model;

    public LogInPresenter(AccountViewModel model) {
        this.model = model;
    }

    @Override
    public void present(LogInResponse response) {
        model.present(switch (response) {
            case LogInResponse.Success success -> {
                final var profile = success.profile();
                yield new SignedIn(profile.userID(), profile.username(), profile.displayName());
            }
            case LogInResponse.Failure failure -> new Error(Field.FORM, switch (failure) {
                case BAD_CREDENTIALS -> "Email or password is incorrect.";
                case NO_USABLE_SESSION ->
                        "We couldn't complete your login. If asked to confirm your email, do that first.";
                case RETRYABLE_FAILURE -> "We couldn't log you in right now. Please try again.";
            });
        });
    }
}
