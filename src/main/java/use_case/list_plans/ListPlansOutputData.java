package use_case.list_plans;

import java.util.List;
import java.util.UUID;

/**
 * Output Data for the List Plans Use Case.
 * Each of the user's plans, sorted by name.
 */
public class ListPlansOutputData {

    private final List<PlanSummary> plans;

    public ListPlansOutputData(List<PlanSummary> plans) {
        this.plans = List.copyOf(plans);
    }

    public List<PlanSummary> getPlans() {
        return plans;
    }

    /** What the list shows for one plan. The id is what Open / Edit / Delete / Start workout get passed. */
    public static class PlanSummary {
        private final UUID planId;
        private final String name;
        private final int exerciseCount;

        public PlanSummary(UUID planId, String name, int exerciseCount) {
            this.planId = planId;
            this.name = name;
            this.exerciseCount = exerciseCount;
        }

        public UUID getPlanId() {
            return planId;
        }

        public String getName() {
            return name;
        }

        public int getExerciseCount() {
            return exerciseCount;
        }
    }
}
