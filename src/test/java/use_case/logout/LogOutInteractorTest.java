package use_case.logout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LogOutInteractorTest {
    private final LogoutFake gateway = new LogoutFake();
    private final List<LogOutResponse> responses = new ArrayList<>();
    private final LogOutInputBoundary interactor = new LogOutInteractor(gateway, response -> {
        assertNull(gateway.currentSession);
        responses.add(response);
    });

    @ParameterizedTest
    @EnumSource(value = LogOutSessionGateway.Revocation.class, names = {"REVOKED", "FAILED"})
    void presentsSignedOutEvenWhenRevocationFails(LogOutSessionGateway.Revocation revocation) {
        final UUID session = UUID.randomUUID();
        gateway.currentSession = session;
        gateway.revocation = revocation;

        interactor.execute(new LogOutRequest());

        assertEquals(List.of(LogOutResponse.SIGNED_OUT), responses);
        assertEquals(List.of(session), gateway.revocationAttempts);
    }

    @Test
    void signedOutLogoutDoesNotTryToRevokeAnotherSession() {
        interactor.execute(new LogOutRequest());

        assertEquals(List.of(LogOutResponse.SIGNED_OUT), responses);
        assertEquals(List.of(), gateway.revocationAttempts);
    }

    private static final class LogoutFake implements LogOutSessionGateway {
        private final List<UUID> revocationAttempts = new ArrayList<>();
        private UUID currentSession;
        private Revocation revocation;

        @Override
        public Revocation logOutCurrentSession() {
            final UUID detachedSession = currentSession;
            currentSession = null;
            if (detachedSession == null) {
                return Revocation.NOT_NEEDED;
            }
            revocationAttempts.add(detachedSession);
            return revocation;
        }
    }
}
