package use_case.edit_plan;

import java.util.UUID;

public record EditPlanOutputData(UUID planId, String name, int exerciseCount) {
}
