package use_case.register;

public interface RegisterOutputBoundary {
    void prepareSuccessView(RegisterOutputData outputData);

    void prepareFailView(RegisterFailure failure);
}
