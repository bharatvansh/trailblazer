package com.trailblazer.plugin.networking;

import com.trailblazer.api.PathData;
import com.trailblazer.api.Vector3d;
import com.trailblazer.plugin.networking.payload.s2c.PathActionResultPayload;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for network payload serialization.
 * Ensures payloads can be encoded to bytes correctly.
 */
class PayloadSerializationTest {

    @Test
    void pathActionResultPayloadSerializesWithAllFields() {
        UUID pathId = UUID.randomUUID();
        PathData pathData = new PathData(
            pathId,
            "TestPath",
            UUID.randomUUID(),
            "Owner",
            System.currentTimeMillis(),
            "minecraft:overworld",
            List.of(new Vector3d(1.0, 2.0, 3.0))
        );

        PathActionResultPayload payload = new PathActionResultPayload(
            "RENAME",
            pathId,
            true,
            "Success",
            pathData,
            12345L,
            67890L
        );

        byte[] bytes = payload.toBytes();
        
        assertNotNull(bytes);
        assertTrue(bytes.length > 0, "Payload should produce non-empty bytes");
    }

    @Test
    void pathActionResultPayloadSerializesWithNullPathId() {
        PathActionResultPayload payload = new PathActionResultPayload(
            "DELETE",
            null,
            true,
            "Deleted",
            null
        );

        byte[] bytes = payload.toBytes();
        
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
    }

    @Test
    void pathActionResultPayloadChannelIsCorrect() {
        assertEquals("trailblazer:path_action_result", PathActionResultPayload.CHANNEL);
    }
}
