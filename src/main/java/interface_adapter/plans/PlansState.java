package interface_adapter.plans;

import java.util.List;
import java.util.UUID;

/**
 * The State information for the Workout Plans View.
 * The user's plans as the list shows them, plus an error if they couldn't be loaded.
 */
public class PlansState {

    private final List<PlanItem> plans;
    private final String error;

    public PlansState(List<PlanItem> plans, String error) {
        this.plans = List.copyOf(plans);
        this.error = error;
    }

    /** Nothing loaded yet. */
    public static PlansState empty() {
        return new PlansState(List.of(), null);
    }

    public List<PlanItem> getPlans() {
        return plans;
    }

    public String getError() {
        return error;
    }

    /** One row of the list. The id is what Open / Edit / Delete / Start workout get passed. */
    public static class PlanItem {
        private final UUID planId;
        private final String name;
        private final String details;

        public PlanItem(UUID planId, String name, String details) {
            this.planId = planId;
            this.name = name;
            this.details = details;
        }

        public UUID getPlanId() {
            return planId;
        }

        public String getName() {
            return name;
        }

        /** The small line under the name, like "6 exercises". */
        public String getDetails() {
            return details;
        }
    }
}
