package com.trailblazer.plugin.commands;

/**
 * Small utility helpers for command argument parsing.
 */
public final class CommandUtils {

    private CommandUtils() {}

    public static final class ParseResult {
        public final String value;
        public final int nextIndex;

        public ParseResult(String value, int nextIndex) {
            this.value = value;
            this.nextIndex = nextIndex;
        }
    }

    /**
     * Parse a possibly-quoted argument starting at index `start`.
     * If greedy==true and the first token isn't quoted, the rest of the args
     * will be joined and returned. If greedy==false and the first token isn't
     * quoted, only the single token at `start` is returned.
     *
     * Examples:
     * args=["view","\"My Path\"","extra"] -> value="My Path", nextIndex=2
     * args=["view","My","Path"] with greedy=true -> value="My Path", nextIndex=3
     * args=["rename","Old","New Name"] for rename parsing: parse first non-greedy, then rest greedy.
     */
    public static ParseResult parseQuoted(String[] args, int start, boolean greedy) {
        if (args == null || start >= args.length) {
            return new ParseResult("", start);
        }

        String first = args[start];
        if (first == null) return new ParseResult("", start + 1);

        // quoted with double or single quote
        if ((first.startsWith("\"") && first.length() > 1) || (first.startsWith("'") && first.length() > 1)) {
            char quote = first.charAt(0);
            StringBuilder sb = new StringBuilder();
            // strip leading quote
            String t = first.substring(1);
            if (t.endsWith(String.valueOf(quote))) {
                // single-token quoted
                sb.append(t, 0, t.length() - 1);
                return new ParseResult(sb.toString(), start + 1);
            }
            sb.append(t);
            for (int i = start + 1; i < args.length; i++) {
                sb.append(' ').append(args[i]);
                String s = args[i];
                if (s.endsWith(String.valueOf(quote))) {
                    // remove trailing quote
                    int removeLen = s.length() - 1;
                    // replace the appended trailing token to drop the quote
                    int lastSpace = sb.lastIndexOf(" ");
                    if (lastSpace >= 0) {
                        sb.replace(lastSpace + 1, sb.length(), s.substring(0, removeLen));
                    }
                    return new ParseResult(sb.toString(), i + 1);
                }
            }
            // no closing quote found — return what we have (without adding a trailing quote)
            return new ParseResult(sb.toString(), args.length);
        }

        if (greedy) {
            StringBuilder sb = new StringBuilder(first);
            for (int i = start + 1; i < args.length; i++) {
                sb.append(' ').append(args[i]);
            }
            return new ParseResult(sb.toString(), args.length);
        }

        return new ParseResult(first, start + 1);
    }
}
