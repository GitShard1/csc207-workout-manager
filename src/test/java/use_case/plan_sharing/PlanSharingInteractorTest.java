package use_case.plan_sharing;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static use_case.plan_sharing.PlanSharingException.Reason.INVALID_CODE;
import static use_case.plan_sharing.PlanSharingException.Reason.INVALID_REQUEST;
import static use_case.plan_sharing.PlanSharingException.Reason.SIGN_IN_REQUIRED;

final class PlanSharingInteractorTest {
    private static final UUID VIEWER = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final String CODE = "abcda001-0002-4003-8004-000000000005";
    private static final String REVISION = "a".repeat(64);
    private static final Instant EXPIRY = Instant.parse("2026-12-01T00:00:00Z");
    private final RecordingGateway gateway = new RecordingGateway();
    private final PlanSharingInteractor sharing = new PlanSharingInteractor(gateway);

    @Test
    void forwardsPlanActionsWithTheInitiatingAccount() throws Exception {
        assertEquals(List.of(new PlanSharingGateway.PlanSummary(PLAN_ID, "Push", EXPIRY)), sharing.list(VIEWER));
        assertEquals(new PlanSharingGateway.GeneratedCode(CODE, EXPIRY), sharing.generate(VIEWER, PLAN_ID));
        sharing.revoke(VIEWER, PLAN_ID);

        assertEquals(List.of(List.of(VIEWER), List.of(VIEWER, PLAN_ID), List.of(VIEWER, PLAN_ID)), gateway.requests);
    }

    @Test
    void normalizesShareCodesAndPreservesTheCopyRequestIdAcrossRetries() throws Exception {
        final String pastedCode = "  " + CODE.toUpperCase(Locale.ROOT) + "\n";

        assertEquals("Push", sharing.preview(VIEWER, pastedCode).name());
        assertEquals(sharing.copy(VIEWER, pastedCode, REVISION, REQUEST_ID),
                sharing.copy(VIEWER, CODE, REVISION, REQUEST_ID));

        final List<Object> copy = List.of(VIEWER, CODE, REVISION, REQUEST_ID);
        assertEquals(List.of(List.of(VIEWER, CODE), copy, copy), gateway.requests);
    }

    @Test
    void rejectsInvalidActionsBeforeTouchingTheGateway() {
        assertEquals(SIGN_IN_REQUIRED, assertThrows(PlanSharingException.class, () -> sharing.list(null)).reason());
        assertEquals(INVALID_REQUEST,
                assertThrows(PlanSharingException.class, () -> sharing.generate(VIEWER, null)).reason());
        assertEquals(INVALID_REQUEST,
                assertThrows(PlanSharingException.class, () -> sharing.revoke(VIEWER, null)).reason());
        assertEquals(INVALID_CODE,
                assertThrows(PlanSharingException.class, () -> sharing.preview(VIEWER, "invalid")).reason());
        assertEquals(INVALID_CODE,
                assertThrows(PlanSharingException.class, () -> sharing.preview(VIEWER, null)).reason());
        assertEquals(INVALID_CODE, assertThrows(PlanSharingException.class,
                () -> sharing.preview(VIEWER, "abcda001-0002-3003-8004-000000000005")).reason());
        assertEquals(INVALID_REQUEST, assertThrows(PlanSharingException.class,
                () -> sharing.copy(VIEWER, CODE, "invalid", REQUEST_ID)).reason());
        assertEquals(INVALID_REQUEST, assertThrows(PlanSharingException.class,
                () -> sharing.copy(VIEWER, CODE, REVISION, null)).reason());
        assertEquals(List.of(), gateway.requests);
    }

    private static final class RecordingGateway implements PlanSharingGateway {
        private final List<List<Object>> requests = new ArrayList<>();

        @Override
        public List<PlanSummary> list(UUID viewer) {
            requests.add(List.of(viewer));
            return List.of(new PlanSummary(PLAN_ID, "Push", EXPIRY));
        }

        @Override
        public GeneratedCode generate(UUID viewer, UUID planId) {
            requests.add(List.of(viewer, planId));
            return new GeneratedCode(CODE, EXPIRY);
        }

        @Override
        public void revoke(UUID viewer, UUID planId) {
            requests.add(List.of(viewer, planId));
        }

        @Override
        public Preview preview(UUID viewer, String code) {
            requests.add(List.of(viewer, code));
            return new Preview("Push", List.of(new ExerciseTarget("Bench", 3, 8, 60.0)), REVISION);
        }

        @Override
        public CopyResult copy(UUID viewer, String code, String revision, UUID requestId) {
            requests.add(List.of(viewer, code, revision, requestId));
            return new CopyResult(PLAN_ID, "Push (copy)");
        }
    }
}
