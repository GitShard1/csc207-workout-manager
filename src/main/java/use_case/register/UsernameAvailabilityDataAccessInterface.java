package use_case.register;

public interface UsernameAvailabilityDataAccessInterface {
    /** Returns true only if another account is confirmed to hold this username. */
    boolean isTaken(String username);
}
