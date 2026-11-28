package interface_adapter.log_workout;

import org.junit.jupiter.api.Test;
import use_case.workout_common.SetEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkoutFormatTest {
    @Test
    void weightDropsTrailingZerosTest() {
        assertEquals("60", WorkoutFormat.weight(60.0));
        assertEquals("62.5", WorkoutFormat.weight(62.5));
        assertEquals("0", WorkoutFormat.weight(0.0));
        assertEquals("100", WorkoutFormat.weight(100.0));
    }

    @Test
    void setTest() {
        assertEquals("62.5 kg x 8", WorkoutFormat.set(62.5, 8));
    }

    @Test
    void setsTest() {
        assertEquals("60 kg x 8, 62.5 kg x 6",
                WorkoutFormat.sets(List.of(new SetEntry(60.0, 8), new SetEntry(62.5, 6))));
        assertEquals("", WorkoutFormat.sets(List.of()));
    }

    @Test
    void targetTest() {
        assertEquals("3 x 8 at 60 kg", WorkoutFormat.target(3, 8, 60.0));
        assertEquals("5 x 5", WorkoutFormat.target(5, 5, null));
    }
}
