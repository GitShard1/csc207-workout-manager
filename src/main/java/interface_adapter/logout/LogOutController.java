package interface_adapter.logout;

import use_case.logout.LogOutInputBoundary;
import use_case.logout.LogOutRequest;

public final class LogOutController {
    private final LogOutInputBoundary input;

    public LogOutController(LogOutInputBoundary input) {
        this.input = input;
    }

    public void logOut() {
        input.execute(new LogOutRequest());
    }
}
