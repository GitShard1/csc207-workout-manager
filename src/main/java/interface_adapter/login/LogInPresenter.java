package interface_adapter.login;

import interface_adapter.ViewManagerModel;
import interface_adapter.home.HomeViewModel;
import use_case.login.LogInFailure;
import use_case.login.LogInOutputBoundary;
import use_case.login.LogInOutputData;

public final class LogInPresenter implements LogInOutputBoundary {
    private final ViewManagerModel viewManager;
    private final LogInViewModel logIn;
    private final HomeViewModel home;

    public LogInPresenter(ViewManagerModel viewManager, LogInViewModel logIn, HomeViewModel home) {
        this.viewManager = viewManager;
        this.logIn = logIn;
        this.home = home;
    }

    @Override
    public void prepareSuccessView(LogInOutputData outputData) {
        home.showSignedIn(outputData.profile());
        logIn.setState(LogInState.empty());
        viewManager.setState(home.getViewName());
    }

    @Override
    public void prepareFailView(LogInFailure failure) {
        logIn.setState(new LogInState(message(failure), null));
    }

    private static String message(LogInFailure failure) {
        return switch (failure) {
            case BAD_CREDENTIALS -> "Email or password is incorrect.";
            case NO_USABLE_SESSION -> "Confirm your email if prompted, then try logging in again.";
            case RETRYABLE_FAILURE -> "We couldn't log you in right now. Please try again.";
        };
    }
}
