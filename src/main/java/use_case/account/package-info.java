/**
 * Authentication calls are synchronous. The caller must serialize Register, Log In, and Log Out against
 * the shared account gateway and invoke Register and Log In only while signed out.
 * The gateway implementation owns the in-memory session; use-case results expose only the current profile.
 */
package use_case.account;
