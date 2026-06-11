package com.barbu.app.protocol;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ChatFilterTest {

    private final ChatFilter filter = new ChatFilter();

    @Test
    void leaves_clean_text_untouched() {
        assertEquals("bien joué !", filter.sanitize("bien joué !"));
    }

    @Test
    void masks_a_banned_word_keeping_length() {
        assertEquals("****", filter.sanitize("merde"));
    }

    @Test
    void masking_is_case_insensitive() {
        assertEquals("****", filter.sanitize("MeRdE"));
    }

    @Test
    void masks_every_occurrence_inside_a_sentence() {
        assertEquals("oh **** et ****", filter.sanitize("oh merde et putain"));
    }
}
