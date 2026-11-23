package interface_adapter;

import interface_adapter.AccountViewModel.Error;
import interface_adapter.AccountViewModel.Field;
import interface_adapter.login.LogInPresenter;
import interface_adapter.register.RegisterPresenter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import use_case.login.LogInResponse;
import use_case.register.RegisterResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountRecoveryPresenterTest {
    private final AccountViewModel model = new AccountViewModel();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "SIGNUP_UNCERTAIN | We couldn't confirm registration. Try logging in before registering again.",
        "ACCOUNT_CREATED_PROFILE_UNAVAILABLE | Your account was created, but we couldn't load your profile. "
                + "Try logging in.",
        "NO_USABLE_SESSION | Confirm your email if prompted, then log in.",
        "RETRYABLE_FAILURE | We couldn't complete registration. Please try again later."
    })
    void registrationRecoveryReflectsWhatHappenedToTheAccount(RegisterResponse.Failure failure, String instruction) {
        new RegisterPresenter(model).present(failure);

        assertEquals(new Error(Field.FORM, instruction), model.takeOutcome());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "BAD_CREDENTIALS | Email or password is incorrect.",
        "NO_USABLE_SESSION | Confirm your email if prompted, then try logging in again.",
        "RETRYABLE_FAILURE | We couldn't log you in right now. Please try again."
    })
    void loginRecoveryReflectsTheFailure(LogInResponse.Failure failure, String instruction) {
        new LogInPresenter(model).present(failure);

        assertEquals(new Error(Field.FORM, instruction), model.takeOutcome());
    }
}
