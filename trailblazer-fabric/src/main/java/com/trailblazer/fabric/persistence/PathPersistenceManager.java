package com.trailblazer.fabric.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.trailblazer.api.PathData;
import com.trailblazer.api.Vector3d;
import com.trailblazer.fabric.ClientPathManager;
import com.trailblazer.fabric.config.TrailblazerClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles persistence of local paths for singleplayer or plugin-less servers.
 */
public class PathPersistenceManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("trailblazer-persist");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final ClientPathManager pathManager;
    private final TrailblazerClientConfig config;

    private Path worldDir; // base directory for current world (paths folder)
    private final Map<UUID, Boolean> dirty = new ConcurrentHashMap<>();

    private static final String INDEX_FILE = "index.json";

    public PathPersistenceManager(ClientPathManager pathManager, TrailblazerClientConfig config) {
        this.pathManager = pathManager;
        this.config = config;
    }

    public void setWorldDirectory(Path worldSaveRoot) {
        if (worldSaveRoot == null) {
            this.worldDir = null;
            return;
        }
        this.worldDir = worldSaveRoot.resolve("trailblazer").resolve("paths");
        try {
            Files.createDirectories(this.worldDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create trailblazer paths directory", e);
        }
    }

    /** Called on world join after worldDir set */
    public void loadAll() {
        if (worldDir == null) return;
        // read index
        Path index = worldDir.resolve(INDEX_FILE);
        Set<UUID> listed = new HashSet<>();
        if (Files.isRegularFile(index)) {
            try (BufferedReader r = Files.newBufferedReader(index)) {
                Type type = new TypeToken<List<IndexEntry>>(){}.getType();
                List<IndexEntry> entries = GSON.fromJson(r, type);
                if (entries != null) {
                    for (IndexEntry e : entries) {
                        if (e.pathId == null || e.fileName == null) continue;
                        listed.add(e.pathId);
                        loadSingle(e.pathId, worldDir.resolve(e.fileName));
                    }
                }
            } catch (Exception ex) {
                LOGGER.error("Failed reading index.json", ex);
            }
        }
        // pick up orphans
        try {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(worldDir, "*.json")) {
                for (Path p : ds) {
                    if (p.getFileName().toString().equals(INDEX_FILE)) continue;
                    // parse uuid from filename ( <uuid>.json )
                    String base = p.getFileName().toString();
                    if (base.endsWith(".json")) {
                        String uuidPart = base.substring(0, base.length()-5);
                        try {
                            UUID id = UUID.fromString(uuidPart);
                            if (!listed.contains(id)) {
                                loadSingle(id, p);
                            }
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error scanning path directory", e);
        }
    }

    private void loadSingle(UUID pathId, Path file) {
        try (BufferedReader r = Files.newBufferedReader(file)) {
            PathFileRecord rec = GSON.fromJson(r, PathFileRecord.class);
            if (rec == null) return;
            // basic validation
            if (!pathId.equals(rec.pathId)) return;
            List<Vector3d> pts = rec.points != null ? rec.points : List.of();
            PathData data = new PathData(rec.pathId, rec.name != null? rec.name : "Path", rec.ownerUUID != null? rec.ownerUUID : UUID.randomUUID(),
                    rec.ownerName != null? rec.ownerName : "Player", rec.creationTimestamp != null? rec.creationTimestamp : System.currentTimeMillis(),
                    rec.dimension != null? rec.dimension : "minecraft:overworld", new ArrayList<>(pts), rec.color != null? rec.color : 0);
            pathManager.addMyPath(data);
            pathManager.setPathVisible(data.getPathId());
        } catch (Exception e) {
            LOGGER.error("Failed to load path file {}", file, e);
        }
    }

    public void markDirty(UUID pathId) {
        if (pathManager != null && !pathManager.isLocalPath(pathId)) {
            return;
        }
        dirty.put(pathId, Boolean.TRUE);
    }

    public void saveDirty() { saveSelected(dirty.keySet()); }

    public void saveAll() {
        Set<UUID> all = new HashSet<>();
        for (PathData data : pathManager.getMyPaths()) {
            UUID id = data.getPathId();
            if (pathManager.isLocalPath(id)) {
                all.add(id);
            }
        }
        saveSelected(all);
    }

    private void saveSelected(Collection<UUID> ids) {
        if (worldDir == null) return;
        List<UUID> processed = new ArrayList<>();
        for (UUID id : ids) {
            if (!pathManager.isLocalPath(id)) {
                processed.add(id);
                continue;
            }
            PathData data = pathManager.getMyPaths().stream().filter(p -> p.getPathId().equals(id)).findFirst().orElse(null);
            if (data == null) {
                processed.add(id);
                continue;
            }
            Path file = worldDir.resolve(id.toString() + ".json");
            Path tmp = worldDir.resolve(id.toString() + ".json.tmp");
            try (BufferedWriter w = Files.newBufferedWriter(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                PathFileRecord rec = PathFileRecord.from(data);
                GSON.toJson(rec, w);
            } catch (IOException e) {
                LOGGER.error("Failed to write temp path file {}", tmp, e);
                continue;
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                LOGGER.error("Failed atomic move for path file {}", file, e);
            }
            processed.add(id);
        }
        if (!processed.isEmpty()) {
            dirty.keySet().removeAll(processed);
        }
        writeIndex();
    }

    private void writeIndex() {
        if (worldDir == null) return;
        List<IndexEntry> entries = new ArrayList<>();
        for (PathData data : pathManager.getMyPaths()) {
            if (!pathManager.isLocalPath(data.getPathId())) {
                continue;
            }
            entries.add(new IndexEntry(data.getPathId(), data.getPathId() + ".json"));
        }
        Path idx = worldDir.resolve(INDEX_FILE);
        Path tmp = worldDir.resolve(INDEX_FILE + ".tmp");
        try (BufferedWriter w = Files.newBufferedWriter(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            GSON.toJson(entries, w);
        } catch (IOException e) {
            LOGGER.error("Failed to write index temp", e);
            return;
        }
        try {
            Files.move(tmp, idx, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.error("Failed to move index", e);
        }
    }

    public void deleteLocal(UUID pathId) {
        if (worldDir == null) return;
        Path file = worldDir.resolve(pathId.toString() + ".json");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOGGER.error("Failed to delete path file {}", file, e);
        }
        dirty.remove(pathId);
        writeIndex();
    }

    /** Thin path points if exceeding limit; returns true if modified. */
    public boolean enforcePointLimit(PathData data) {
        int limit = config.maxPointsPerPath;
        if (limit <= 0) return false;
        List<Vector3d> pts = data.getPoints();
        if (pts.size() <= limit) return false;
        int keepEvery = 2;
        while ((pts.size() / keepEvery) > limit) {
            keepEvery++;
        }
        List<Vector3d> thinned = new ArrayList<>();
        for (int i = 0; i < pts.size(); i += keepEvery) {
            thinned.add(pts.get(i));
        }
        // ensure last point retained
        if (!thinned.get(thinned.size()-1).equals(pts.get(pts.size()-1))) {
            thinned.add(pts.get(pts.size()-1));
        }
        pts.clear();
        pts.addAll(thinned);
        markDirty(data.getPathId());
        return true;
    }

    // --- Data holder classes ---
    private static class IndexEntry {
        UUID pathId;
        String fileName;
        IndexEntry(UUID id, String f) { this.pathId = id; this.fileName = f; }
    }

    private static class PathFileRecord {
    @SuppressWarnings("unused")
    int schemaVersion = 1; // retained for forward compatibility
        UUID pathId;
        String name;
        UUID ownerUUID;
        String ownerName;
        Long creationTimestamp;
        String dimension;
        Integer color;
        List<Vector3d> points;

        static PathFileRecord from(PathData d) {
            PathFileRecord r = new PathFileRecord();
            r.pathId = d.getPathId();
            r.name = d.getPathName();
            r.ownerUUID = d.getOwnerUUID();
            r.ownerName = d.getOwnerName();
            r.creationTimestamp = d.getCreationTimestamp();
            r.dimension = d.getDimension();
            r.color = d.getColorArgb();
            r.points = new ArrayList<>(d.getPoints());
            return r;
        }
    }
}
