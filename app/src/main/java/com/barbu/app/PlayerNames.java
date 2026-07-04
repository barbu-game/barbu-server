package com.barbu.app;

/**
 * Shared length rules for player names. Accounts must be 1–{@value #MAX_LEN} characters; guest names
 * are trimmed and clamped to the same ceiling, which matches the {@code VARCHAR(40)} storage columns
 * so a long name can never overflow the database insert.
 */
public final class PlayerNames {
    public static final int MAX_LEN = 40;

    private PlayerNames() {}

    public static boolean isValidAccountName(String name) {
        if (name == null) {
            return false;
        }
        String trimmed = name.trim();
        return !trimmed.isEmpty() && trimmed.codePointCount(0, trimmed.length()) <= MAX_LEN;
    }

    /**
     * Trim a guest-supplied name and clamp it to {@link #MAX_LEN} code points (never splitting a
     * surrogate pair). Returns {@code null} for a null or blank name so the caller can apply its own
     * fallback.
     */
    public static String normalizeGuest(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.codePointCount(0, trimmed.length()) <= MAX_LEN) {
            return trimmed;
        }
        return trimmed.substring(0, trimmed.offsetByCodePoints(0, MAX_LEN));
    }
}
