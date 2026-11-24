package use_case.login;

/** Why logging in failed. The presenter decides what to tell the user. */
public enum LogInFailure {
    BAD_CREDENTIALS,
    NO_USABLE_SESSION,
    RETRYABLE_FAILURE
}
