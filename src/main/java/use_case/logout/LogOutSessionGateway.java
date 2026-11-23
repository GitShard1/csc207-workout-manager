package use_case.logout;

public interface LogOutSessionGateway {
    /**
     * Serialized with the other account operations. Detach the current session and profile before attempting remote
     * revocation of that session only. Remote failure must not restore local state.
     */
    void logOutCurrentSession();
}
