package com.trailblazer.api;

import java.util.regex.Pattern;

/**
 * Utility for sanitizing user-provided path names to ensure safe persistence and display.
 * <p>Rules:
 * <ul>
 *   <li>Null or blank -> "Unnamed Path"</li>
 *   <li>Trim surrounding whitespace</li>
 *   <li>Clamp length to {@link #MAX_PATH_NAME_LENGTH}</li>
 *   <li>Allow only letters, digits, space, underscore, hyphen</li>
 *   <li>All other characters replaced with underscore</li>
 * </ul>
 * The logic is intentionally conservative to avoid issues with file systems, logs, and network payloads.
 */
public final class PathNameSanitizer {

    public static final int MAX_PATH_NAME_LENGTH = 64;
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9 _-]+$");
    private static final Pattern INVALID_CHARS = Pattern.compile("[^a-zA-Z0-9 _-]");

    private PathNameSanitizer() {}

    /**
     * Sanitize a proposed path name according to project security rules.
     * @param name raw user input (may be null or blank)
     * @return sanitized, non-null, non-empty name
     */
    public static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "Unnamed Path";
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_PATH_NAME_LENGTH) {
            trimmed = trimmed.substring(0, MAX_PATH_NAME_LENGTH);
        }
        if (!VALID_NAME_PATTERN.matcher(trimmed).matches()) {
            trimmed = INVALID_CHARS.matcher(trimmed).replaceAll("_");
        }
        // If result contains no alphanumeric characters, revert to default to avoid meaningless underscore strings
        if (trimmed.isBlank() || !trimmed.matches(".*[A-Za-z0-9].*")) {
            return "Unnamed Path";
        }
        return trimmed;
    }
}
