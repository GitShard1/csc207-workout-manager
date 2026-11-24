package use_case.register;

import use_case.account.AccountProfile;

/** The profile of the user who is now signed in. */
public record RegisterOutputData(AccountProfile profile) { }
