package use_case.login;

import use_case.account.AccountProfile;

/** The profile of the user who is now signed in. */
public record LogInOutputData(AccountProfile profile) { }
