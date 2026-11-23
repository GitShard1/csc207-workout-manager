package use_case.account;

import entity.User;

import java.util.Optional;

public interface CurrentUserReader {
    /** Returns the fully authenticated current user, or empty while signed out. */
    Optional<User> currentUser();
}
