package com.barbu.app.room;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.app.room.CasualMatchmaker.Candidate;
import com.barbu.app.room.CasualMatchmaker.Formation;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CasualMatchmakerDecisionTest {

    private static final long FILL = 8_000;

    @Test
    void forms_a_full_human_table_as_soon_as_size_players_wait() {
        List<Candidate> waiting = List.of(new Candidate(0), new Candidate(10), new Candidate(20));
        Optional<Formation> f = CasualMatchmaker.decideFormation(waiting, 100, FILL, 2);
        assertTrue(f.isPresent());
        assertEquals(List.of(0, 1), f.get().indices(), "takes the two oldest waiters");
        assertEquals(0, f.get().botsToAdd());
    }

    @Test
    void does_not_form_below_size_before_the_fill_timeout() {
        List<Candidate> waiting = List.of(new Candidate(0));
        assertTrue(CasualMatchmaker.decideFormation(waiting, 5_000, FILL, 4).isEmpty());
    }

    @Test
    void bot_fills_the_incomplete_table_after_the_fill_timeout() {
        List<Candidate> waiting = List.of(new Candidate(0), new Candidate(500));
        Optional<Formation> f = CasualMatchmaker.decideFormation(waiting, 9_000, FILL, 4);
        assertTrue(f.isPresent());
        assertEquals(List.of(0, 1), f.get().indices());
        assertEquals(2, f.get().botsToAdd(), "4-seat table, 2 humans → 2 bots");
    }

    @Test
    void empty_queue_forms_nothing() {
        assertTrue(CasualMatchmaker.decideFormation(List.of(), 100_000, FILL, 4).isEmpty());
    }
}
