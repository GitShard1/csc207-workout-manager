package use_case.list_plans;

/**
 * The input boundary for the List Plans Use Case.
 */
public interface ListPlansInputBoundary {

    /** Lists the signed-in user's workout plans. */
    void execute();
}
