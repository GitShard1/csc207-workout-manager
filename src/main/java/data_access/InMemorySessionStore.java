package data_access;

import entity.User;
import use_case.account.AuthSession;
import use_case.account.CurrentUserDataAccessInterface;
import use_case.account.StartSessionDataAccessInterface;
import use_case.logout.LogOutSessionDataAccessInterface;

import java.util.Optional;
import java.util.UUID;

/**
 * Holds the signed-in user for the lifetime of the app. Supabase DAOs use the session token so their requests
 * run as that user and row-level security applies.
 */
public final class InMemorySessionStore
        implements StartSessionDataAccessInterface, LogOutSessionDataAccessInterface, CurrentUserDataAccessInterface {
    private AuthSession session;
    private User user;

    @Override
    public synchronized void start(AuthSession newSession, User newUser) {
        if (!newSession.userId().equals(newUser.getUserID())) {
            throw new IllegalArgumentException("The profile does not belong to this session.");
        }
        session = newSession;
        user = newUser;
    }

    @Override
    public synchronized Optional<AuthSession> endCurrentSession() {
        final Optional<AuthSession> ended = Optional.ofNullable(session);
        session = null;
        user = null;
        return ended;
    }

    @Override
    public synchronized Optional<User> currentUser() {
        return Optional.ofNullable(user);
    }

    public synchronized Optional<String> accessToken() {
        return session == null ? Optional.empty() : Optional.of(session.accessToken());
    }

    /** Binds a request to the account checked by its use case, even if the current account changes. */
    public synchronized Optional<String> accessTokenFor(UUID userId) {
        return session != null && session.userId().equals(userId) ? accessToken() : Optional.empty();
    }
}
