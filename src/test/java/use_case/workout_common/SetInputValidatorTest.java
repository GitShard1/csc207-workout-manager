package use_case.workout_common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SetInputValidatorTest {

    @Test
    void validTest() {
        assertEquals("", SetInputValidator.checkSet(60.0, 8));
    }

    @Test
    void validBodyweightTest() {
        assertEquals("", SetInputValidator.checkSet(0.0, 10));
    }

    @Test
    void failureNegativeWeightTest() {
        assertEquals("Weight can't be negative.", SetInputValidator.checkSet(-5.0, 8));
    }

    @Test
    void failureNanWeightTest() {
        assertEquals("Weight must be a number.", SetInputValidator.checkSet(Double.NaN, 8));
    }

    @Test
    void failureInfiniteWeightTest() {
        assertEquals("Weight must be a number.", SetInputValidator.checkSet(Double.POSITIVE_INFINITY, 8));
        assertEquals("Weight must be a number.", SetInputValidator.checkSet(Double.NEGATIVE_INFINITY, 8));
    }

    @Test
    void failureZeroRepsTest() {
        assertEquals("Reps must be at least 1.", SetInputValidator.checkSet(60.0, 0));
    }

    @Test
    void failureNegativeRepsTest() {
        assertEquals("Reps must be at least 1.", SetInputValidator.checkSet(60.0, -3));
    }

    @Test
    void failureWeightCheckedFirstTest() {
        assertEquals("Weight can't be negative.", SetInputValidator.checkSet(-5.0, 0));
    }
}
