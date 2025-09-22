package com.trailblazer.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.trailblazer.api.PathData;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Manages the persistence of PathData objects by saving them to and loading them from JSON files.
 */
public class PathDataManager {

    private final File dataFolder;
    private final Gson gson;

    public PathDataManager(TrailblazerPlugin plugin) {
        // Create a dedicated folder for our data inside the plugin's folder.
        this.dataFolder = new File(plugin.getDataFolder(), "data");
        if (!this.dataFolder.exists()) {
            if (!this.dataFolder.mkdirs()) {
                TrailblazerPlugin.getPluginLogger().severe("Could not create data folder!");
            }
        }
        // Use Gson for JSON serialization. prettyPrinting makes the files human-readable.
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Saves a new path for a specific player.
     * It loads existing paths and appends the new one to the list.
     * @param player The player who owns the path.
     * @param path The PathData object to save.
     */
    public void savePath(Player player, PathData path) {
        File playerFile = getPlayerFile(player.getUniqueId());
        List<PathData> playerPaths = loadPaths(player.getUniqueId());
        playerPaths.add(path);

        try (FileWriter writer = new FileWriter(playerFile)) {
            gson.toJson(playerPaths, writer);
        } catch (IOException e) {
            TrailblazerPlugin.getPluginLogger().severe("Failed to save path data for " + player.getName());
            e.printStackTrace();
        }
    }

    /**
     * Loads all saved paths for a specific player.
     * @param playerUUID The UUID of the player.
     * @return A list of PathData objects. Returns an empty list if no file exists or on error.
     */
    public List<PathData> loadPaths(UUID playerUUID) {
        File playerFile = getPlayerFile(playerUUID);
        if (!playerFile.exists()) {
            return new ArrayList<>(); // Return an empty list if the player has no saved paths yet.
        }

        try (FileReader reader = new FileReader(playerFile)) {
            PathData[] paths = gson.fromJson(reader, PathData[].class);
            if (paths != null) {
                return new ArrayList<>(Arrays.asList(paths));
            }
        } catch (IOException e) {
            TrailblazerPlugin.getPluginLogger().severe("Failed to load path data for UUID " + playerUUID);
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    /**
     * Helper method to get the specific data file for a player.
     * @param playerUUID The player's UUID.
     * @return The File object for the player's data file.
     */
    private File getPlayerFile(UUID playerUUID) {
        return new File(dataFolder, playerUUID.toString() + ".json");
    }
}