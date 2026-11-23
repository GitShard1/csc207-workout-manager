package interface_adapter.logout;

import interface_adapter.AccountViewModel;
import interface_adapter.AccountViewModel.SignedOut;
import use_case.logout.LogOutOutputBoundary;
import use_case.logout.LogOutResponse;

public final class LogOutPresenter implements LogOutOutputBoundary {
    private final AccountViewModel model;

    public LogOutPresenter(AccountViewModel model) {
        this.model = model;
    }

    @Override
    public void present(LogOutResponse response) {
        model.present(switch (response) {
            case SIGNED_OUT -> SignedOut.SIGNED_OUT;
        });
    }
}
