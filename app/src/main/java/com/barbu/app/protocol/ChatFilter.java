package com.barbu.app.protocol;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masque les mots interdits par un nombre fixe d'étoiles. Volontairement minimaliste
 * (liste statique) : un vrai filtre de modération dépasse le périmètre de cette feature.
 */
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
