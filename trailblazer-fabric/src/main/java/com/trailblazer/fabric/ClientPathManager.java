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
    // A map to hold all of the player's own paths.
    private final Map<UUID, PathData> myPaths = new HashMap<>();
    // A map to hold all paths shared with the player.
    private final Map<UUID, PathData> sharedPaths = new HashMap<>();
    // A set to track which paths should currently be visible.
    private final Set<UUID> visiblePaths = new HashSet<>();
    // A special field to hold the path currently being recorded in real-time.
    private PathData livePath = null;
    // Tracks whether a recording session is currently active (client view)
    private boolean recording = false;

    public void addMyPath(PathData path) {
        myPaths.put(path.getPathId(), path);
    }

    public void addSharedPath(PathData path) {
        sharedPaths.put(path.getPathId(), path);
        setPathVisible(path.getPathId()); // Make shared paths visible by default
    }

    public void removePath(UUID pathId) {
        myPaths.remove(pathId);
        sharedPaths.remove(pathId);
        visiblePaths.remove(pathId);
    }

    public void setPathVisible(UUID pathId) {
        if (myPaths.containsKey(pathId) || sharedPaths.containsKey(pathId)) {
            visiblePaths.add(pathId);
        }
    }

    public void setPathHidden(UUID pathId) {
        visiblePaths.remove(pathId);
    }

    public void togglePathVisibility(UUID pathId) {
        if (visiblePaths.contains(pathId)) {
            setPathHidden(pathId);
        } else {
            setPathVisible(pathId);
        }
    }

    public void deletePath(UUID pathId) {
        myPaths.remove(pathId);
        visiblePaths.remove(pathId);
    }

    public void removeSharedPath(UUID pathId) {
        sharedPaths.remove(pathId);
        visiblePaths.remove(pathId);
    }

    public boolean isPathVisible(UUID pathId) {
        return visiblePaths.contains(pathId);
    }

    public void hideAllPaths() {
        visiblePaths.clear();
    }

    /**
     * Returns the path currently being recorded, if any.
     * @return The live PathData object, or null if not recording.
     */
    public PathData getLivePath() {
        return livePath;
    }

    /**
     * Updates the points for the live path. This is called when a {@code LivePathUpdatePayload} is received.
     * @param points The new list of points for the path.
     */
    public void updateLivePath(List<Vector3d> points) {
        if (livePath == null) {
            // If this is the first update, create a new PathData object to represent the live path.
            // We use a fixed UUID for the live path so we can easily identify it.
            UUID livePathId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            livePath = new PathData(livePathId, "LiveRecording", UUID.randomUUID(), "Me", 0L, "", points);
        } else {
            // Otherwise, just update the points.
            livePath.getPoints().clear();
            livePath.getPoints().addAll(points);
        }
        // Receiving live updates implies we are in a recording session.
        recording = true;
    }

    /**
     * Clears the live path data. Called when a {@code StopLivePathPayload} is received.
     */
    public void stopLivePath() {
        livePath = null;
        recording = false;
    }

    // --- Recording state helpers (client-side optimistic) ---
    public boolean isRecording() {
        return recording;
    }

    public void startRecordingLocal() {
        recording = true;
    }

    public void stopRecordingLocal() {
        recording = false;
    }

    public Collection<PathData> getVisiblePaths() {
        List<PathData> result = new ArrayList<>();
        for (UUID id : visiblePaths) {
            PathData path = myPaths.get(id);
            if (path == null) {
                path = sharedPaths.get(id);
            }
            if (path != null) {
                result.add(path);
            }
        }
        return result;
    }

    public Collection<PathData> getMyPaths() {
        return myPaths.values();
    }

    public Collection<PathData> getSharedPaths() {
        return sharedPaths.values();
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

        addMyPath(dummyPath);
        setPathVisible(dummyId);
        TrailblazerFabricClient.LOGGER.info("Loaded dummy path for rendering test.");
    }
}