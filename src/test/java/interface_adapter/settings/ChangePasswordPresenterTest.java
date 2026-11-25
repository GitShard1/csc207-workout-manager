package interface_adapter.settings;

import org.junit.jupiter.api.Test;
import use_case.change_password.ChangePasswordOutputData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChangePasswordPresenterTest {
    private final SettingsViewModel settingsViewModel = new SettingsViewModel();
    private final ChangePasswordPresenter presenter = new ChangePasswordPresenter(settingsViewModel);

    @Test
    void successShowsMessageTest() {
        presenter.prepareSuccessView(new ChangePasswordOutputData("jirat"));

        assertEquals("Password changed for @jirat.", settingsViewModel.getState().getPasswordMessage());
        assertNull(settingsViewModel.getState().getPasswordError());
    }

    @Test
    void failShowsErrorTest() {
        presenter.prepareFailView("New passwords don't match.");

        assertEquals("New passwords don't match.", settingsViewModel.getState().getPasswordError());
        assertNull(settingsViewModel.getState().getPasswordMessage());
    }

    @Test
    void errorAfterSuccessClearsTheMessageTest() {
        // Only one of error / message should show at a time
        presenter.prepareSuccessView(new ChangePasswordOutputData("jirat"));
        presenter.prepareFailView("Current password is incorrect.");

        assertEquals("Current password is incorrect.", settingsViewModel.getState().getPasswordError());
        assertNull(settingsViewModel.getState().getPasswordMessage());
    }

    @Test
    void viewIsToldAboutTheChangeTest() {
        int[] notifications = {0};
        settingsViewModel.addPropertyChangeListener(event -> notifications[0]++);

        presenter.prepareFailView("Password inputted is empty.");

        assertEquals(1, notifications[0]);
    }
}
