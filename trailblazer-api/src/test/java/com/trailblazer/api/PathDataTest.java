package com.trailblazer.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.UUID;

import org.junit.jupiter.api.Test;

public class PathDataTest {

    @Test
    void constructor_shouldDefaultOriginToSelf() {
        UUID pathId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        PathData path = new PathData(pathId, "Test", ownerId, "Owner", System.currentTimeMillis(), "minecraft:overworld", new ArrayList<>());

        assertEquals(pathId, path.getOriginPathId());
        assertEquals(ownerId, path.getOriginOwnerUUID());
        assertEquals("Owner", path.getOriginOwnerName());
    }

    @Test
    void setOrigin_shouldUpdateLineageFields() {
        UUID pathId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        PathData path = new PathData(pathId, "Test", ownerId, "Owner", 0L, "minecraft:overworld", new ArrayList<>());

        UUID originPathId = UUID.randomUUID();
        UUID originOwnerId = UUID.randomUUID();
        String originOwnerName = "Origin";
        path.setOrigin(originPathId, originOwnerId, originOwnerName);

        assertEquals(originPathId, path.getOriginPathId());
        assertEquals(originOwnerId, path.getOriginOwnerUUID());
        assertEquals(originOwnerName, path.getOriginOwnerName());
    }

    @Test
    void name_shouldBeSanitizedOnSet() {
        UUID pathId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        PathData path = new PathData(pathId, "Ok", ownerId, "Owner", 0L, "minecraft:overworld", new ArrayList<>());

        String input = "Bad<>Name";
        path.setPathName(input);
        assertEquals(PathNameSanitizer.sanitize(input), path.getPathName());
    }

    @Test
    void color_shouldBeLazilyAssignedAndStable() {
        UUID pathId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        PathData path = new PathData(pathId, "Test", ownerId, "Owner", 0L, "minecraft:overworld", new ArrayList<>());

        int c1 = path.getColorArgb();
        int c2 = path.getColorArgb();

        assertNotEquals(0, c1);
        assertEquals(c1, c2);
    }
}
