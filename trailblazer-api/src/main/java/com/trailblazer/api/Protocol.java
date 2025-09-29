package com.trailblazer.api;

/**
 * Protocol constants shared between client and server.
 */
public final class Protocol {

    private Protocol() {}

    /** Protocol version for compatibility checks. */
    public static final int PROTOCOL_VERSION = 1;

    /** Server capability flags. */
    public static final class Capability {
        private Capability() {}
        /** Live incremental updates. */
        public static final int LIVE_UPDATES = 1 << 0;
        /** Shared server storage. */
        public static final int SHARED_STORAGE = 1 << 1;
        /** Permission enforcement. */
        public static final int PERMISSIONS = 1 << 2;
        /** Server assigns canonical colors. */
        public static final int COLOR_CANONICAL = 1 << 3;
        /** Server path optimization. */
        public static final int SERVER_THINNING = 1 << 4;
        /** Multi-dimension support. */
        public static final int MULTI_DIMENSION_FILTER = 1 << 5;
    }

    /** Tests if a capability flag is set. */
    public static boolean has(int mask, int flag) { return (mask & flag) != 0; }
}
