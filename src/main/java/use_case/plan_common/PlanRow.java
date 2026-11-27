package use_case.plan_common;

/** One exercise row of a plan, as entered by the user. */
public interface PlanRow {
    String exerciseId();

    int sets();

    int reps();

    Double weight();
}
