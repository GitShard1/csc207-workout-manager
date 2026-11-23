package interface_adapter;

import interface_adapter.AccountViewModel.SignedIn;
import interface_adapter.login.LogInPresenter;
import interface_adapter.register.RegisterPresenter;
import org.junit.jupiter.api.Test;
import use_case.account.AccountProfile;
import use_case.login.LogInResponse;
import use_case.register.RegisterResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AccountViewModelTest {
    @Test
    void bothPresentersPublishAConsumableSignedInProfile() {
        final AccountViewModel model = new AccountViewModel();
        final AccountProfile login = new AccountProfile(UUID.randomUUID(), "first", "First User");
        final AccountProfile registration = new AccountProfile(UUID.randomUUID(), "second", "Second User");

        new LogInPresenter(model).present(new LogInResponse.Success(login));
        assertEquals(new SignedIn(login.userID(), "first", "First User"), model.takeOutcome());
        assertNull(model.takeOutcome());

        new RegisterPresenter(model).present(new RegisterResponse.Success(registration));
        assertEquals(new SignedIn(registration.userID(), "second", "Second User"), model.takeOutcome());
        assertNull(model.takeOutcome());
    }
}
