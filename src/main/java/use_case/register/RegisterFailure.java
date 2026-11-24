package use_case.register;

/** Why registration failed. The presenter decides what to tell the user. */
public enum RegisterFailure {
    INVALID_EMAIL,
    INVALID_USERNAME,
    INVALID_DISPLAY_NAME,
    PASSWORD_TOO_SHORT,
    PASSWORD_REJECTED,
    PASSWORD_MISMATCH,
    USERNAME_TAKEN,
    ACCOUNT_UNAVAILABLE,
    SIGNUP_UNCERTAIN,
    NO_USABLE_SESSION,
    ACCOUNT_CREATED_PROFILE_UNAVAILABLE,
    RETRYABLE_FAILURE
}
