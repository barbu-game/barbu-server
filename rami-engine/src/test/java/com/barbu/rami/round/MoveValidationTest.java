package com.barbu.rami.round;

import static com.barbu.rami.TestCards.cs;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class MoveValidationTest {

    @Test
    void malformed_moves_are_rejected_as_illegal_arguments() {
        assertThrows(IllegalArgumentException.class, () -> new RamiMove.Draw(null));
        assertThrows(IllegalArgumentException.class, () -> new RamiMove.Open(null));
        assertThrows(IllegalArgumentException.class, () -> new RamiMove.Open(Arrays.asList((List<Integer>) null)));
        assertThrows(IllegalArgumentException.class, () -> new RamiMove.NewMeld(Arrays.asList(1, null)));
        assertThrows(IllegalArgumentException.class, () -> new RamiMove.LayOff(0, null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new RamiMove.LayOff(0, List.of(), null));
    }

    @Test
    void null_move_is_rejected() {
        RamiRound r = RamiRoundEngine.setUp(
                0,
                Phase.DRAW,
                List.of(cs("5S", "6S", "7S"), cs("2C"), cs("2D"), cs("2S")),
                cs("9H"),
                cs("KD"),
                List.of(),
                Collections.nCopies(4, false));
        assertThrows(IllegalArgumentException.class, () -> RamiRoundEngine.apply(r, 0, null));
    }
}
