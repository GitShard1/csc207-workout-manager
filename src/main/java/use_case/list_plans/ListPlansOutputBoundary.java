package use_case.list_plans;

/**
 * The output boundary for the List Plans Use Case.
 */
public interface ListPlansOutputBoundary {

    /** The plans were loaded (the list can be empty). */
    void prepareSuccessView(ListPlansOutputData outputData);

    /** The plans couldn't be loaded, and errorMessage says why. */
    void prepareFailView(String errorMessage);
}
