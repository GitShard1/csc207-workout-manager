package use_case.change_password;

import entity.User;
import org.junit.jupiter.api.Test;
import use_case.account.CurrentUserDataAccessInterface;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePasswordInteractorTest {
    private static final User USER = new User(UUID.randomUUID(), "jirat", "Jirat");

    private final FakeUserDataAccessObject userDataAccessObject = new FakeUserDataAccessObject();
    private final RecordingPresenter presenter = new RecordingPresenter();
    private CurrentUserDataAccessInterface currentUserDataAccessObject = () -> Optional.of(USER);

    private void run(String currentPassword, String newPassword, String confirmPassword) {
        ChangePasswordInteractor interactor =
                new ChangePasswordInteractor(userDataAccessObject, currentUserDataAccessObject, presenter);
        interactor.execute(new ChangePasswordInputData(currentPassword, newPassword, confirmPassword));
    }

    @Test
    void successTest() {
        run("oldpassword", "newpassword", "newpassword");

        assertEquals("oldpassword", userDataAccessObject.currentPasswordGiven);
        assertEquals("newpassword", userDataAccessObject.newPasswordGiven);
        assertEquals("jirat", presenter.successUsername);
        assertNull(presenter.error);
    }

    @Test
    void exactlyMinimumLengthIsAllowedTest() {
        run("oldpassword", "12345678", "12345678");

        assertEquals("jirat", presenter.successUsername);
    }

    @Test
    void tooShortFailsTest() {
        run("oldpassword", "1234567", "1234567");

        assertEquals("New password must be at least 8 characters.", presenter.error);
        assertFalse(userDataAccessObject.called);
    }

    @Test
    void notLoggedInFailsTest() {
        currentUserDataAccessObject = Optional::empty;

        run("oldpassword", "newpassword", "newpassword");

        assertEquals("You need to be logged in to change your password.", presenter.error);
        assertFalse(userDataAccessObject.called);
    }

    @Test
    void emptyCurrentPasswordFailsTest() {
        run("", "newpassword", "newpassword");

        assertEquals("Password inputted is empty.", presenter.error);
        assertFalse(userDataAccessObject.called);
    }

    @Test
    void confirmDoesNotMatchFailsTest() {
        run("oldpassword", "newpassword", "newpasswordd");

        assertEquals("New passwords don't match.", presenter.error);
        assertFalse(userDataAccessObject.called);
    }

    @Test
    void sameAsCurrentFailsTest() {
        run("samepassword", "samepassword", "samepassword");

        assertEquals("New password must be different from your current one.", presenter.error);
        assertFalse(userDataAccessObject.called);
    }

    @Test
    void wrongCurrentPasswordFailsTest() {
        userDataAccessObject.result = ChangePasswordUserDataAccessInterface.Result.WRONG_CURRENT_PASSWORD;

        run("wrongpassword", "newpassword", "newpassword");

        assertTrue(userDataAccessObject.called);
        assertEquals("Current password is incorrect.", presenter.error);
        assertNull(presenter.successUsername);
    }

    @Test
    void rejectedByServiceFailsTest() {
        userDataAccessObject.result = ChangePasswordUserDataAccessInterface.Result.REJECTED;

        run("oldpassword", "newpassword", "newpassword");

        assertEquals("That new password wasn't accepted. Try a stronger one.", presenter.error);
    }

    @Test
    void serviceUnavailableFailsTest() {
        userDataAccessObject.result = ChangePasswordUserDataAccessInterface.Result.UNAVAILABLE;

        run("oldpassword", "newpassword", "newpassword");

        assertEquals("Couldn't change your password right now. Please try again.", presenter.error);
    }

    /** Stands in for Supabase. Remembers what it was asked, and answers with result. */
    private static class FakeUserDataAccessObject implements ChangePasswordUserDataAccessInterface {
        private Result result = Result.CHANGED;
        private boolean called;
        private String currentPasswordGiven;
        private String newPasswordGiven;

        @Override
        public Result changePassword(String currentPassword, String newPassword) {
            called = true;
            currentPasswordGiven = currentPassword;
            newPasswordGiven = newPassword;
            return result;
        }
    }

    /** Remembers which view the interactor asked for. */
    private static class RecordingPresenter implements ChangePasswordOutputBoundary {
        private String successUsername;
        private String error;

        @Override
        public void prepareSuccessView(ChangePasswordOutputData outputData) {
            successUsername = outputData.getUsername();
        }

        @Override
        public void prepareFailView(String errorMessage) {
            error = errorMessage;
        }
    }
}
