package com.trailblazer.plugin;

import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;

import com.trailblazer.plugin.commands.PathCommand;
import com.trailblazer.plugin.commands.PathTabCompleter;
import com.trailblazer.plugin.listeners.PlayerMoveListener;
import com.trailblazer.plugin.networking.ServerPacketHandler;

public final class TrailblazerPlugin extends JavaPlugin {

    private static Logger pluginLogger;
    private static TrailblazerPlugin instance;

    private PathRecordingManager pathRecordingManager;
    private PathDataManager pathDataManager;
    private PathRendererManager pathRendererManager;
    private ServerPacketHandler serverPacketHandler;

    @Override
    public void onEnable() {
        instance = this;
        pluginLogger = this.getLogger();

        initializeManagers();
        registerEventListeners();
        registerCommands();

        pluginLogger.info("Plugin enabled successfully.");
        pluginLogger.info("Ready to blaze some trails!");
    }

    @Override
    public void onDisable() {
        // Stop all rendering tasks on shutdown
        getServer().getScheduler().cancelTasks(this);
        pluginLogger.info("Plugin disabled. All trails are safe.");
    }

    private void initializeManagers() {
        // Initialize dataManager first
        pathDataManager = new PathDataManager(this);
        // Create packet handler next
        serverPacketHandler = new ServerPacketHandler(this);
        // Create recording manager with packet handler
        pathRecordingManager = new PathRecordingManager(serverPacketHandler);
        // Set the recording manager in packet handler
        serverPacketHandler.setRecordingManager(pathRecordingManager);
        // Initialize renderer manager
        pathRendererManager = new PathRendererManager(this);
        pluginLogger.info("Managers initialized.");
    }

    private void registerEventListeners() {
        getServer().getPluginManager().registerEvents(new PlayerMoveListener(pathRecordingManager), this);
        pluginLogger.info("Event listeners registered.");
    }

    // --- NEW METHOD ---
    private void registerCommands() {
        getCommand("path").setExecutor(new PathCommand(this));
        getCommand("path").setTabCompleter(new PathTabCompleter(pathDataManager));
        pluginLogger.info("Commands registered.");
    }
    // --- END NEW ---

    public PathRecordingManager getPathRecordingManager() {
        return pathRecordingManager;
    }

    // --- NEW GETTER ---
    public PathDataManager getPathDataManager() {
        return pathDataManager;
    }
    // --- END NEW ---

    public PathRendererManager getPathRendererManager() {
        return pathRendererManager;
    }

    public ServerPacketHandler getServerPacketHandler() {
        return serverPacketHandler;
    }

    public static TrailblazerPlugin getInstance() {
        return instance;
    }

    public static Logger getPluginLogger() {
        return pluginLogger;
    }
}