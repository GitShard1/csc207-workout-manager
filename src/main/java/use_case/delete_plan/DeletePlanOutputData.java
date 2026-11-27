package use_case.delete_plan;

import java.util.UUID;

public record DeletePlanOutputData(UUID planId, String name) {
}
