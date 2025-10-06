package com.trailblazer.api;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class PathDataTest {

    @Test
    void constructorRequiresNonNullFields() {
        UUID pathId = UUID.randomUUID();
        UUID ownerUUID = UUID.randomUUID();
        String ownerName = "TestOwner";
        String dimension = "minecraft:overworld";
        List<Vector3d> points = new ArrayList<>();

        assertThrows(NullPointerException.class, () -> 
            new PathData(null, "Test", ownerUUID, ownerName, System.currentTimeMillis(), dimension, points)
        );
        
        assertThrows(NullPointerException.class, () -> 
            new PathData(pathId, "Test", null, ownerName, System.currentTimeMillis(), dimension, points)
        );
        
        assertThrows(NullPointerException.class, () -> 
            new PathData(pathId, "Test", ownerUUID, null, System.currentTimeMillis(), dimension, points)
        );
        
        assertThrows(NullPointerException.class, () -> 
            new PathData(pathId, "Test", ownerUUID, ownerName, System.currentTimeMillis(), null, points)
        );
        
        assertThrows(NullPointerException.class, () -> 
            new PathData(pathId, "Test", ownerUUID, ownerName, System.currentTimeMillis(), dimension, null)
        );
    }

    @Test
    void constructorSanitizesPathName() {
        UUID pathId = UUID.randomUUID();
        UUID ownerUUID = UUID.randomUUID();
        String dimension = "minecraft:overworld";
        List<Vector3d> points = new ArrayList<>();

        PathData path = new PathData(
            pathId, 
            "Test<>Path!!", 
            ownerUUID, 
            "Owner", 
            System.currentTimeMillis(), 
            dimension, 
            points
        );

        assertEquals("Test__Path__", path.getPathName());
    }

    @Test
    void constructorSetsOriginToSelf() {
        UUID pathId = UUID.randomUUID();
        UUID ownerUUID = UUID.randomUUID();
        String dimension = "minecraft:overworld";
        List<Vector3d> points = new ArrayList<>();

        PathData path = new PathData(
            pathId, 
            "Test", 
            ownerUUID, 
            "Owner", 
            System.currentTimeMillis(), 
            dimension, 
            points
        );

        assertEquals(pathId, path.getOriginPathId());
        assertEquals(ownerUUID, path.getOriginOwnerUUID());
        assertEquals("Owner", path.getOriginOwnerName());
    }

    @Test
    void constructorWithColorAssignsNonZeroColor() {
        UUID pathId = UUID.randomUUID();
        UUID ownerUUID = UUID.randomUUID();
        String dimension = "minecraft:overworld";
        List<Vector3d> points = new ArrayList<>();

        PathData path = new PathData(
            pathId, 
            "Test", 
            ownerUUID, 
            "Owner", 
            System.currentTimeMillis(), 
            dimension, 
            points,
            0 // Zero should trigger color assignment
        );

        assertNotEquals(0, path.getColorArgb());
    }

    @Test
    void lazyColorAssignment() {
        UUID pathId = UUID.randomUUID();
        UUID ownerUUID = UUID.randomUUID();
        String dimension = "minecraft:overworld";
        List<Vector3d> points = new ArrayList<>();

        PathData path = new PathData(
            pathId, 
            "Test", 
            ownerUUID, 
            "Owner", 
            System.currentTimeMillis(), 
            dimension, 
            points
        );

        // First call should assign a color
        int color = path.getColorArgb();
        assertNotEquals(0, color);
        
        // Second call should return the same color
        assertEquals(color, path.getColorArgb());
    }

    @Test
    void setColorArgbIgnoresZero() {
        UUID pathId = UUID.randomUUID();
        UUID ownerUUID = UUID.randomUUID();
        String dimension = "minecraft:overworld";
        List<Vector3d> points = new ArrayList<>();

        PathData path = new PathData(
            pathId, 
            "Test", 
            ownerUUID, 
            "Owner", 
            System.currentTimeMillis(), 
            dimension, 
            points
        );

        int originalColor = path.getColorArgb();
        path.setColorArgb(0);
        
        // Color should remain unchanged
        assertEquals(originalColor, path.getColorArgb());
    }

    @Test
    void setColorArgbUpdatesNonZero() {
        UUID pathId = UUID.randomUUID();
        UUID ownerUUID = UUID.randomUUID();
        String dimension = "minecraft:overworld";
        List<Vector3d> points = new ArrayList<>();

        PathData path = new PathData(
            pathId, 
            "Test", 
            ownerUUID, 
            "Owner", 
            System.currentTimeMillis(), 
            dimension, 
            points
        );

        int newColor = 0xFFFF0000;
        path.setColorArgb(newColor);
        assertEquals(newColor, path.getColorArgb());
    }

    @Test
    void setPathNameSanitizes() {
        UUID pathId = UUID.randomUUID();
        UUID ownerUUID = UUID.randomUUID();
        String dimension = "minecraft:overworld";
        List<Vector3d> points = new ArrayList<>();

        PathData path = new PathData(
            pathId, 
            "Original", 
            ownerUUID, 
            "Owner", 
            System.currentTimeMillis(), 
            dimension, 
            points
        );

        path.setPathName("New<>Name!!");
        assertEquals("New__Name__", path.getPathName());
    }

    @Test
    void setOriginUpdatesFields() {
        UUID pathId = UUID.randomUUID();
        UUID ownerUUID = UUID.randomUUID();
        String dimension = "minecraft:overworld";
        List<Vector3d> points = new ArrayList<>();

        PathData path = new PathData(
            pathId, 
            "Test", 
            ownerUUID, 
            "Owner", 
            System.currentTimeMillis(), 
            dimension, 
            points
        );

        UUID newOriginId = UUID.randomUUID();
        UUID newOriginOwner = UUID.randomUUID();
        String newOriginName = "OriginalOwner";

        path.setOrigin(newOriginId, newOriginOwner, newOriginName);

        assertEquals(newOriginId, path.getOriginPathId());
        assertEquals(newOriginOwner, path.getOriginOwnerUUID());
        assertEquals(newOriginName, path.getOriginOwnerName());
    }

    @Test
    void equalityBasedOnPathId() {
        UUID pathId = UUID.randomUUID();
        UUID ownerUUID = UUID.randomUUID();
        String dimension = "minecraft:overworld";
        List<Vector3d> points = new ArrayList<>();

        PathData path1 = new PathData(
            pathId, 
            "Name1", 
            ownerUUID, 
            "Owner1", 
            System.currentTimeMillis(), 
            dimension, 
            points
        );

        PathData path2 = new PathData(
            pathId, 
            "Name2", 
            ownerUUID, 
            "Owner2", 
            System.currentTimeMillis(), 
            dimension, 
            new ArrayList<>()
        );

        assertEquals(path1, path2);
        assertEquals(path1.hashCode(), path2.hashCode());
    }

    @Test
    void differentPathIdsNotEqual() {
        UUID ownerUUID = UUID.randomUUID();
        String dimension = "minecraft:overworld";
        List<Vector3d> points = new ArrayList<>();

        PathData path1 = new PathData(
            UUID.randomUUID(), 
            "Test", 
            ownerUUID, 
            "Owner", 
            System.currentTimeMillis(), 
            dimension, 
            points
        );

        PathData path2 = new PathData(
            UUID.randomUUID(), 
            "Test", 
            ownerUUID, 
            "Owner", 
            System.currentTimeMillis(), 
            dimension, 
            points
        );

        assertNotEquals(path1, path2);
    }

    @Test
    void gettersReturnCorrectValues() {
        UUID pathId = UUID.randomUUID();
        UUID ownerUUID = UUID.randomUUID();
        String ownerName = "TestOwner";
        String dimension = "minecraft:overworld";
        long timestamp = System.currentTimeMillis();
        List<Vector3d> points = List.of(
            new Vector3d(1.0, 2.0, 3.0),
            new Vector3d(4.0, 5.0, 6.0)
        );

        PathData path = new PathData(
            pathId, 
            "TestPath", 
            ownerUUID, 
            ownerName, 
            timestamp, 
            dimension, 
            new ArrayList<>(points)
        );

        assertEquals(pathId, path.getPathId());
        assertEquals("TestPath", path.getPathName());
        assertEquals(ownerUUID, path.getOwnerUUID());
        assertEquals(ownerName, path.getOwnerName());
        assertEquals(timestamp, path.getCreationTimestamp());
        assertEquals(dimension, path.getDimension());
        assertEquals(2, path.getPoints().size());
        assertNotNull(path.getSharedWith());
    }

    @Test
    void sharedWithListInitializedEmpty() {
        UUID pathId = UUID.randomUUID();
        UUID ownerUUID = UUID.randomUUID();
        String dimension = "minecraft:overworld";
        List<Vector3d> points = new ArrayList<>();

        PathData path = new PathData(
            pathId, 
            "Test", 
            ownerUUID, 
            "Owner", 
            System.currentTimeMillis(), 
            dimension, 
            points
        );

        assertThat(path.getSharedWith()).isEmpty();
    }

    @Test
    void constructorWithSharedWithPopulatesList() {
        UUID pathId = UUID.randomUUID();
        UUID ownerUUID = UUID.randomUUID();
        String dimension = "minecraft:overworld";
        List<Vector3d> points = new ArrayList<>();
        List<UUID> sharedWith = List.of(UUID.randomUUID(), UUID.randomUUID());

        PathData path = new PathData(
            pathId, 
            "Test", 
            ownerUUID, 
            "Owner", 
            System.currentTimeMillis(), 
            dimension, 
            points,
            0xFFFF0000,
            sharedWith
        );

        assertEquals(2, path.getSharedWith().size());
    }

    @Test
    void toStringContainsRelevantInfo() {
        UUID pathId = UUID.randomUUID();
        UUID ownerUUID = UUID.randomUUID();
        String dimension = "minecraft:overworld";
        List<Vector3d> points = List.of(new Vector3d(1.0, 2.0, 3.0));

        PathData path = new PathData(
            pathId, 
            "TestPath", 
            ownerUUID, 
            "Owner", 
            System.currentTimeMillis(), 
            dimension, 
            new ArrayList<>(points)
        );

        String str = path.toString();
        assertThat(str)
            .contains("PathData")
            .contains(pathId.toString())
            .contains("TestPath")
            .contains(ownerUUID.toString())
            .contains("pointCount=1");
    }
}
