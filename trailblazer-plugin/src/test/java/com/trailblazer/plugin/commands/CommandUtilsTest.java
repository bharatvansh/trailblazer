package com.trailblazer.plugin.commands;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CommandUtilsTest {

    @Test
    void testParseQuoted_Simple() {
        String[] args = {"view", "\"My", "Path\""};
        CommandUtils.ParseResult result = CommandUtils.parseQuoted(args, 1, true);
        assertEquals("My Path", result.value);
        assertEquals(3, result.nextIndex);
    }

    @Test
    void testParseQuoted_NoQuotes() {
        String[] args = {"view", "MyPath"};
        CommandUtils.ParseResult result = CommandUtils.parseQuoted(args, 1, false);
        assertEquals("MyPath", result.value);
        assertEquals(2, result.nextIndex);
    }

    @Test
    void testParseQuoted_SeparateStartQuote() {
        // Case: /trailblazer rename "MyPath" " NewPath"
        // args: ["rename", "\"MyPath\"", "\"", "NewPath\""]
        // But wait, Bukkit splits by space.
        // If input is: /trailblazer rename "MyPath" " NewPath"
        // args: [rename, "MyPath", ", NewPath"] -- wait, " NewPath" becomes [", NewPath"] if space is inside quotes?
        // No. Bukkit splits by spaces.
        // If I type: /cmd " a"
        // args: [", a"]

        // If I type: /cmd " a "
        // args: [", a, "]

        // If I type: /trailblazer rename Old " New"
        // args: [rename, Old, ", New"]
        String[] args = {"rename", "Old", "\"", "New\""};

        // Parsing "Old"
        CommandUtils.ParseResult r1 = CommandUtils.parseQuoted(args, 1, false);
        assertEquals("Old", r1.value);
        assertEquals(2, r1.nextIndex);

        // Parsing " New"
        // args[2] is "\""
        // args[3] is "New\""
        CommandUtils.ParseResult r2 = CommandUtils.parseQuoted(args, 2, true);

        // This is expected to fail currently because parseQuoted skips the block if length is 1
        // The failing behavior is likely returning just "\"" if not greedy, or "\" New\"" if greedy?
        // Wait, if greedy=true, and it skips the quoted block:
        // sb appends first arg: "\""
        // then appends space + args[3]: " New\""
        // So it returns "\" New\""
        // But we want " New" (without quotes)

        assertEquals(" New", r2.value, "Should handle quoted string starting with separate quote token");
    }

    @Test
    void testParseQuoted_SpaceAfterQuote() {
        // Input: " My Path"
        // Args: [", My, Path"]
        String[] args = {"\"", "My", "Path\""};
        CommandUtils.ParseResult result = CommandUtils.parseQuoted(args, 0, true);
        assertEquals(" My Path", result.value);
    }
}
