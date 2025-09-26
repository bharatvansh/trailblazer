package com.trailblazer.api;

/**
 * Shared protocol constants between client mod and server plugin.
 * Keeping them in the API ensures both sides compile against the same numbers.
 */
public final class Protocol {

    private Protocol() {}

    /** Increment when packet formats / semantics change incompatibly. */
    public static final int PROTOCOL_VERSION = 1;

    /** Capability bitmask flags advertised by server (and optionally client). */
    public static final class Capability {
        private Capability() {}
        /** Supports live incremental path updates (vs only full sync). */
        public static final int LIVE_UPDATES = 1 << 0;
        /** Server provides shared persistent storage for paths. */
        public static final int SHARED_STORAGE = 1 << 1;
        /** Permission/quota enforcement active. */
        public static final int PERMISSIONS = 1 << 2;
        /** Server canonicalizes / assigns colors (non-client guessed). */
        public static final int COLOR_CANONICAL = 1 << 3;
        /** Server may thin / decimate paths for performance. */
        public static final int SERVER_THINNING = 1 << 4;
        /** Multi-dimension filtering supported in queries / sync. */
        public static final int MULTI_DIMENSION_FILTER = 1 << 5;
    }

    /** Utility to test a bit. */
    public static boolean has(int mask, int flag) { return (mask & flag) != 0; }
}
