package interface_adapter.settings;

import entity.User;
import org.junit.jupiter.api.Test;
import use_case.change_password.ChangePasswordInteractor;
import use_case.change_password.ChangePasswordOutputBoundary;
import use_case.change_password.ChangePasswordOutputData;
import use_case.change_password.ChangePasswordUserDataAccessInterface;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChangePasswordControllerTest {

    @Test
    void passwordsArriveInTheRightOrderTest() {
        // ca-login's controller swaps its arguments, so check ours ends up current -> current, new -> new
        List<String> received = new ArrayList<>();
        ChangePasswordUserDataAccessInterface fakeDataAccessObject = (currentPassword, newPassword) -> {
            received.add(currentPassword);
            received.add(newPassword);
            return ChangePasswordUserDataAccessInterface.Result.CHANGED;
        };

        User user = new User(UUID.randomUUID(), "jirat", "Jirat");
        ChangePasswordInteractor interactor =
                new ChangePasswordInteractor(fakeDataAccessObject, () -> Optional.of(user), new DoNothingPresenter());
        ChangePasswordController controller = new ChangePasswordController(interactor);

        controller.execute("oldpassword", "newpassword", "newpassword");

        assertEquals(List.of("oldpassword", "newpassword"), received);
    }

    /** The presenter doesn't matter for this test. */
    private static class DoNothingPresenter implements ChangePasswordOutputBoundary {
        @Override
        public void prepareSuccessView(ChangePasswordOutputData outputData) { }

        @Override
        public void prepareFailView(String errorMessage) { }
    }
}
