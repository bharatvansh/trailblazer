package com.trailblazer.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PathNameSanitizerTest {

    @Test
    void nullOrBlankBecomesUnnamed() {
        assertEquals("Unnamed Path", PathNameSanitizer.sanitize(null));
        assertEquals("Unnamed Path", PathNameSanitizer.sanitize("   "));
    }

    @Test
    void trimsAndLimitsLength() {
        String longName = "A".repeat(PathNameSanitizer.MAX_PATH_NAME_LENGTH + 10);
        String sanitized = PathNameSanitizer.sanitize(longName);
        assertEquals(PathNameSanitizer.MAX_PATH_NAME_LENGTH, sanitized.length());
    }

    @Test
    void replacesInvalidChars() {
        String input = "Path!*&Name<>?";
        String out = PathNameSanitizer.sanitize(input);
    assertEquals("Path___Name___", out); // Each invalid char replaced individually
        assertTrue(out.matches("[A-Za-z0-9 _-]+"));
    }

    @Test
    void preservesValidChars() {
        String input = "Valid_Name-123 Test";
        assertEquals(input, PathNameSanitizer.sanitize(input));
    }

    @Test
    void allInvalidResultsInDefault() {
        String input = "@@@@@@@";
        assertEquals("Unnamed Path", PathNameSanitizer.sanitize(input));
    }
}
