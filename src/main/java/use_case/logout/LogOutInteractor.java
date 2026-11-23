package use_case.logout;

public final class LogOutInteractor implements LogOutInputBoundary {
    private final LogOutSessionGateway gateway;
    private final LogOutOutputBoundary output;

    public LogOutInteractor(LogOutSessionGateway gateway, LogOutOutputBoundary output) {
        this.gateway = gateway;
        this.output = output;
    }

    @Override
    public void execute(LogOutRequest request) {
        gateway.logOutCurrentSession();
        output.present(LogOutResponse.SIGNED_OUT);
    }
}
