package com.trailblazer.fabric;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.trailblazer.api.PathData;
import com.trailblazer.api.Vector3d;

/**
 * Manages path data on the client side.
 * For now, it's a simple in-memory store.
 */
public class ClientPathManager {
    // A map to hold all paths the client is aware of, keyed by their unique ID.
    private final Map<UUID, PathData> paths = new HashMap<>();
    // A set to track which paths should currently be visible.
    private final Set<UUID> visiblePaths = new HashSet<>();

    public void addPath(PathData path) {
        paths.put(path.getPathId(), path);
    }

    public void removePath(UUID pathId) {
        paths.remove(pathId);
        visiblePaths.remove(pathId);
    }

    public void setPathVisible(UUID pathId) {
        if (paths.containsKey(pathId)) {
            visiblePaths.add(pathId);
        }
    }

    public void setPathHidden(UUID pathId) {
        visiblePaths.remove(pathId);
    }

    public void hideAllPaths() {
        visiblePaths.clear();
    }

    public Collection<PathData> getVisiblePaths() {
        List<PathData> result = new ArrayList<>();
        for (UUID id : visiblePaths) {
            if (paths.containsKey(id)) {
                result.add(paths.get(id));
            }
        }
        return result;
    }

    // --- FOR TESTING ---
    public void loadDummyPath() {
        UUID dummyId = UUID.randomUUID();
        List<Vector3d> points = Arrays.asList(
                new Vector3d(0, 100, 0),
                new Vector3d(10, 105, 5),
                new Vector3d(20, 100, 15),
                new Vector3d(15, 98, 25),
                new Vector3d(5, 100, 20),
                new Vector3d(0, 100, 0)
        );
        PathData dummyPath = new PathData(dummyId, "DummyPath", UUID.randomUUID(), "TestPlayer", 0L, "minecraft:overworld", points);

        addPath(dummyPath);
        setPathVisible(dummyId);
        TrailblazerFabricClient.LOGGER.info("Loaded dummy path for rendering test.");
    }
}