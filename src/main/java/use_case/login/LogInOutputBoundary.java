package use_case.login;

public interface LogInOutputBoundary {
    void prepareSuccessView(LogInOutputData outputData);

    void prepareFailView(LogInFailure failure);
}
