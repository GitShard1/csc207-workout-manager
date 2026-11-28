package interface_adapter.plans;

import use_case.list_plans.ListPlansInputBoundary;

/**
 * Controller for the List Plans Use Case.
 */
public class ListPlansController {
    private final ListPlansInputBoundary listPlansUseCaseInteractor;

    public ListPlansController(ListPlansInputBoundary listPlansUseCaseInteractor) {
        this.listPlansUseCaseInteractor = listPlansUseCaseInteractor;
    }

    /** Executes the List Plans Use Case (loads the signed-in user's plans). */
    public void execute() {
        listPlansUseCaseInteractor.execute();
    }
}
