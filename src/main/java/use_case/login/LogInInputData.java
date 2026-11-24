package use_case.login;

public record LogInInputData(String email, String password) {
    @Override
    public String toString() {
        return "LogInInputData[redacted]";
    }
}
