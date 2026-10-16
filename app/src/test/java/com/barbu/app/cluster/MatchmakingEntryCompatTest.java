package com.barbu.app.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.barbu.app.cluster.MatchmakingQueue.Entry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class MatchmakingEntryCompatTest {

    @Test
    void an_entry_queued_by_a_server_without_game_types_reads_as_barbu() throws Exception {
        String legacy = "{\"entryId\":\"e1\",\"userId\":null,\"name\":\"P\",\"rating\":null,"
                + "\"desiredSize\":4,\"homePod\":\"pod-A\",\"enqueuedAt\":5}";
        assertEquals("BARBU", new ObjectMapper().readValue(legacy, Entry.class).gameType());
    }
}
