package use_case.logout;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogOutInteractorTest {
    @Test
    void requestsSessionDetachmentBeforePresentingSignOut() {
        final List<String> events = new ArrayList<>();
        final LogOutInputBoundary interactor = new LogOutInteractor(
                () -> events.add("detach"), () -> events.add("present"));

        interactor.execute();

        assertEquals(List.of("detach", "present"), events);
    }
}
