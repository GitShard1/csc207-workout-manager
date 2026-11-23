package interface_adapter.logout;

import interface_adapter.AccountViewModel;
import interface_adapter.AccountViewModel.SignedOut;
import use_case.logout.LogOutOutputBoundary;

public final class LogOutPresenter implements LogOutOutputBoundary {
    private final AccountViewModel model;

    public LogOutPresenter(AccountViewModel model) {
        this.model = model;
    }

    @Override
    public void signedOut() {
        model.present(SignedOut.SIGNED_OUT);
    }
}
