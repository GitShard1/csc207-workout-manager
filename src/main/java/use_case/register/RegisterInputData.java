package use_case.register;

public record RegisterInputData(String email, String username, String displayName, String password,
                              String passwordConfirmation) {
    @Override
    public String toString() {
        return "RegisterInputData[redacted]";
    }
}
