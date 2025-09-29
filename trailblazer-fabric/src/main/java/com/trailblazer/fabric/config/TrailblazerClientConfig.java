package com.trailblazer.fabric.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client configuration stored as JSON.
 */
public class TrailblazerClientConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("trailblazer-config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "trailblazer-client.json";

    public int maxPointsPerPath = 5000;
    public int autosaveIntervalSeconds = 30;
    public boolean recordingOverlayEnabled = true;
    public String performanceProfile = "balanced";
    public boolean autoRequestShareSync = true;

    public static TrailblazerClientConfig load(Path configDir) {
        try {
            Files.createDirectories(configDir);
            Path file = configDir.resolve(FILE_NAME);
            if (Files.isRegularFile(file)) {
                try (Reader r = Files.newBufferedReader(file)) {
                    TrailblazerClientConfig cfg = GSON.fromJson(r, TrailblazerClientConfig.class);
                    if (cfg != null) return cfg;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load Trailblazer client config, using defaults", e);
        }
        return new TrailblazerClientConfig();
    }

    public void save(Path configDir) {
        try {
            Files.createDirectories(configDir);
            Path file = configDir.resolve(FILE_NAME);
            Path tmp = configDir.resolve(FILE_NAME + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                GSON.toJson(this, w);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.error("Failed to save Trailblazer client config", e);
        }
    }
}
