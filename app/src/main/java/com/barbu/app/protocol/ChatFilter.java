package com.barbu.app.protocol;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Masks banned words. Deliberately minimalist (static list); real moderation is out of scope. */
public final class ChatFilter {

    private static final String MASK = "****";

    private static final Set<String> BANNED = Set.of("merde", "putain");

    private static final Pattern PATTERN =
            Pattern.compile("\\b(" + String.join("|", BANNED) + ")\\b", Pattern.CASE_INSENSITIVE);

    public String sanitize(String text) {
        Matcher matcher = PATTERN.matcher(text);
        return matcher.replaceAll(MASK);
    }
}
