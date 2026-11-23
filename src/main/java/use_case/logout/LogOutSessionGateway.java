package use_case.logout;

public interface LogOutSessionGateway {
    /**
     * Serialized with the other account operations. Detach the current session and profile before attempting remote
     * revocation of that session only. An unavailable service returns FAILED without restoring local state.
     */
    Revocation logOutCurrentSession();

    enum Revocation {
        NOT_NEEDED,
        REVOKED,
        FAILED
    }
}
