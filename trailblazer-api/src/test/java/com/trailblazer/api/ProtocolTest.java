package com.trailblazer.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {

    @Test
    void protocolVersionIsDefined() {
        assertEquals(1, Protocol.PROTOCOL_VERSION);
    }

    @Test
    void capabilityFlagsAreUnique() {
        int liveUpdates = Protocol.Capability.LIVE_UPDATES;
        int sharedStorage = Protocol.Capability.SHARED_STORAGE;
        int permissions = Protocol.Capability.PERMISSIONS;
        int colorCanonical = Protocol.Capability.COLOR_CANONICAL;
        int serverThinning = Protocol.Capability.SERVER_THINNING;
        int multiDimension = Protocol.Capability.MULTI_DIMENSION_FILTER;

        // Each flag should be unique (power of 2)
        assertEquals(1 << 0, liveUpdates);
        assertEquals(1 << 1, sharedStorage);
        assertEquals(1 << 2, permissions);
        assertEquals(1 << 3, colorCanonical);
        assertEquals(1 << 4, serverThinning);
        assertEquals(1 << 5, multiDimension);
    }

    @Test
    void hasReturnsTrueWhenFlagIsSet() {
        int mask = Protocol.Capability.LIVE_UPDATES | Protocol.Capability.PERMISSIONS;

        assertTrue(Protocol.has(mask, Protocol.Capability.LIVE_UPDATES));
        assertTrue(Protocol.has(mask, Protocol.Capability.PERMISSIONS));
    }

    @Test
    void hasReturnsFalseWhenFlagIsNotSet() {
        int mask = Protocol.Capability.LIVE_UPDATES;

        assertFalse(Protocol.has(mask, Protocol.Capability.SHARED_STORAGE));
        assertFalse(Protocol.has(mask, Protocol.Capability.PERMISSIONS));
    }

    @Test
    void hasWorksWithMultipleFlags() {
        int mask = Protocol.Capability.LIVE_UPDATES
                | Protocol.Capability.SHARED_STORAGE
                | Protocol.Capability.PERMISSIONS;

        assertTrue(Protocol.has(mask, Protocol.Capability.LIVE_UPDATES));
        assertTrue(Protocol.has(mask, Protocol.Capability.SHARED_STORAGE));
        assertTrue(Protocol.has(mask, Protocol.Capability.PERMISSIONS));
        assertFalse(Protocol.has(mask, Protocol.Capability.COLOR_CANONICAL));
    }

    @Test
    void hasWorksWithZeroMask() {
        int mask = 0;

        assertFalse(Protocol.has(mask, Protocol.Capability.LIVE_UPDATES));
        assertFalse(Protocol.has(mask, Protocol.Capability.SHARED_STORAGE));
    }

    @Test
    void hasWorksWithAllFlags() {
        int mask = Protocol.Capability.LIVE_UPDATES
                | Protocol.Capability.SHARED_STORAGE
                | Protocol.Capability.PERMISSIONS
                | Protocol.Capability.COLOR_CANONICAL
                | Protocol.Capability.SERVER_THINNING
                | Protocol.Capability.MULTI_DIMENSION_FILTER;

        assertTrue(Protocol.has(mask, Protocol.Capability.LIVE_UPDATES));
        assertTrue(Protocol.has(mask, Protocol.Capability.SHARED_STORAGE));
        assertTrue(Protocol.has(mask, Protocol.Capability.PERMISSIONS));
        assertTrue(Protocol.has(mask, Protocol.Capability.COLOR_CANONICAL));
        assertTrue(Protocol.has(mask, Protocol.Capability.SERVER_THINNING));
        assertTrue(Protocol.has(mask, Protocol.Capability.MULTI_DIMENSION_FILTER));
    }

    @Test
    void capabilityBitMaskingWorks() {
        // Test that we can build up a capability mask
        int mask = 0;
        mask |= Protocol.Capability.LIVE_UPDATES;
        mask |= Protocol.Capability.PERMISSIONS;

        assertEquals(
            Protocol.Capability.LIVE_UPDATES | Protocol.Capability.PERMISSIONS,
            mask
        );
    }
}
