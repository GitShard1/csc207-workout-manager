package interface_adapter.register;

import use_case.register.RegisterInputBoundary;
import use_case.register.RegisterInputData;

public final class RegisterController {
    private final RegisterInputBoundary input;

    public RegisterController(RegisterInputBoundary input) {
        this.input = input;
    }

    public void register(String email, String username, String displayName, String password, String confirmation) {
        input.execute(new RegisterInputData(email, username, displayName, password, confirmation));
    }
}
