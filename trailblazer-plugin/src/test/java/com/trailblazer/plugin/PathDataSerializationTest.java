package com.trailblazer.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.trailblazer.api.PathData;
import com.trailblazer.api.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for path data JSON serialization/deserialization.
 * This ensures persistence compatibility between versions.
 */
class PathDataSerializationTest {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Test
    void pathDataSerializesToJson() {
        UUID pathId = UUID.randomUUID();
        UUID ownerUUID = UUID.randomUUID();
        List<Vector3d> points = List.of(
            new Vector3d(1.0, 2.0, 3.0),
            new Vector3d(4.0, 5.0, 6.0)
        );
        
        PathData path = new PathData(
            pathId,
            "TestPath",
            ownerUUID,
            "TestOwner",
            12345678L,
            "minecraft:overworld",
            new ArrayList<>(points),
            0xFFFF0000
        );

        String json = gson.toJson(path);
        
        assertThat(json).isNotEmpty();
        assertThat(json).contains("TestPath");
        assertThat(json).contains("TestOwner");
        assertThat(json).contains("minecraft:overworld");
    }

    @Test
    void pathDataDeserializesFromJson() {
        String json = """
            {
              "pathId": "550e8400-e29b-41d4-a716-446655440000",
              "pathName": "TestPath",
              "ownerUUID": "550e8400-e29b-41d4-a716-446655440001",
              "ownerName": "TestOwner",
              "creationTimestamp": 12345678,
              "dimension": "minecraft:overworld",
              "points": [
                {"x": 1.0, "y": 2.0, "z": 3.0},
                {"x": 4.0, "y": 5.0, "z": 6.0}
              ],
              "sharedWith": [],
              "colorArgb": -65536
            }
            """;

        PathData path = gson.fromJson(json, PathData.class);
        
        assertNotNull(path);
        assertEquals("TestPath", path.getPathName());
        assertEquals("TestOwner", path.getOwnerName());
        assertEquals("minecraft:overworld", path.getDimension());
        assertEquals(2, path.getPoints().size());
        assertEquals(1.0, path.getPoints().get(0).getX());
    }

    @Test
    void pathDataRoundTripPreservesData() {
        UUID pathId = UUID.randomUUID();
        UUID ownerUUID = UUID.randomUUID();
        List<Vector3d> points = List.of(
            new Vector3d(10.5, 64.0, -20.3),
            new Vector3d(11.2, 64.5, -21.1)
        );
        
        PathData original = new PathData(
            pathId,
            "RoundTrip Path",
            ownerUUID,
            "PlayerName",
            System.currentTimeMillis(),
            "minecraft:the_nether",
            new ArrayList<>(points),
            0xFFABCDEF
        );

        // Serialize
        String json = gson.toJson(original);
        
        // Deserialize
        PathData deserialized = gson.fromJson(json, PathData.class);
        
        assertEquals(original.getPathId(), deserialized.getPathId());
        assertEquals(original.getPathName(), deserialized.getPathName());
        assertEquals(original.getOwnerUUID(), deserialized.getOwnerUUID());
        assertEquals(original.getOwnerName(), deserialized.getOwnerName());
        assertEquals(original.getCreationTimestamp(), deserialized.getCreationTimestamp());
        assertEquals(original.getDimension(), deserialized.getDimension());
        assertEquals(original.getColorArgb(), deserialized.getColorArgb());
        assertEquals(2, deserialized.getPoints().size());
    }

    @Test
    void pathDataWithSharedUsersSerializes() {
        UUID pathId = UUID.randomUUID();
        UUID ownerUUID = UUID.randomUUID();
        List<UUID> sharedWith = List.of(
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        
        PathData path = new PathData(
            pathId,
            "SharedPath",
            ownerUUID,
            "Owner",
            System.currentTimeMillis(),
            "minecraft:overworld",
            new ArrayList<>(),
            0xFF0000,
            sharedWith
        );

        String json = gson.toJson(path);
        PathData deserialized = gson.fromJson(json, PathData.class);
        
        assertEquals(2, deserialized.getSharedWith().size());
    }

    @Test
    void pathDataHandlesMissingOptionalFields() {
        // Old format without origin fields
        String json = """
            {
              "pathId": "550e8400-e29b-41d4-a716-446655440000",
              "pathName": "OldPath",
              "ownerUUID": "550e8400-e29b-41d4-a716-446655440001",
              "ownerName": "OldOwner",
              "creationTimestamp": 12345678,
              "dimension": "minecraft:overworld",
              "points": [],
              "sharedWith": []
            }
            """;

        PathData path = gson.fromJson(json, PathData.class);
        
        assertNotNull(path);
        assertEquals("OldPath", path.getPathName());
        // Should have default origin values
        assertNotNull(path.getOriginPathId());
    }

    @Test
    void pathDataWithLargePointListSerializes() {
        UUID pathId = UUID.randomUUID();
        UUID ownerUUID = UUID.randomUUID();
        List<Vector3d> points = new ArrayList<>();
        
        // Create 1000 points
        for (int i = 0; i < 1000; i++) {
            points.add(new Vector3d(i * 1.0, 64.0, i * 0.5));
        }
        
        PathData path = new PathData(
            pathId,
            "LargePath",
            ownerUUID,
            "Owner",
            System.currentTimeMillis(),
            "minecraft:overworld",
            points
        );

        String json = gson.toJson(path);
        PathData deserialized = gson.fromJson(json, PathData.class);
        
        assertEquals(1000, deserialized.getPoints().size());
        assertEquals(0.0, deserialized.getPoints().get(0).getX());
        assertEquals(999.0, deserialized.getPoints().get(999).getX());
    }

    @Test
    void pathDataSanitizesInvalidNameOnDeserialization() {
        // Tampered JSON with invalid characters
        String json = """
            {
              "pathId": "550e8400-e29b-41d4-a716-446655440000",
              "pathName": "Invalid<>Path!!",
              "ownerUUID": "550e8400-e29b-41d4-a716-446655440001",
              "ownerName": "Owner",
              "creationTimestamp": 12345678,
              "dimension": "minecraft:overworld",
              "points": [],
              "sharedWith": []
            }
            """;

        PathData path = gson.fromJson(json, PathData.class);
        
        // PathData constructor should sanitize the name
        assertEquals("Invalid__Path__", path.getPathName());
    }

    @Test
    void emptyPointsListSerializes() {
        UUID pathId = UUID.randomUUID();
        UUID ownerUUID = UUID.randomUUID();
        
        PathData path = new PathData(
            pathId,
            "EmptyPath",
            ownerUUID,
            "Owner",
            System.currentTimeMillis(),
            "minecraft:overworld",
            new ArrayList<>()
        );

        String json = gson.toJson(path);
        PathData deserialized = gson.fromJson(json, PathData.class);
        
        assertThat(deserialized.getPoints()).isEmpty();
    }
}
