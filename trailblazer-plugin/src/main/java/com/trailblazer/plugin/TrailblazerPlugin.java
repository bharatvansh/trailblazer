package com.trailblazer.plugin;

import java.util.logging.Logger;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.trailblazer.plugin.commands.TrailblazerCommand;
import com.trailblazer.plugin.commands.PathTabCompleter;
import com.trailblazer.plugin.networking.ServerPacketHandler;
import com.trailblazer.plugin.rendering.PlayerRenderSettingsManager;

public final class TrailblazerPlugin extends JavaPlugin implements Listener {

    private static Logger pluginLogger;
    private static TrailblazerPlugin instance;

    private PathDataManager pathDataManager;
    private PathRendererManager pathRendererManager;
    private ServerPacketHandler serverPacketHandler;
    private PlayerRenderSettingsManager playerRenderSettingsManager;

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
        pathDataManager = new PathDataManager(this);
        playerRenderSettingsManager = new PlayerRenderSettingsManager();
        serverPacketHandler = new ServerPacketHandler(this);
        pathRendererManager = new PathRendererManager(this); // This will show an error, we fix it next
        pluginLogger.info("Managers initialized.");
    }

    private void registerEventListeners() {
        getServer().getPluginManager().registerEvents(this, this); // Register this class for the quit event
        pluginLogger.info("Event listeners registered.");
    }
    
    // Cleans up managers to prevent memory leaks when a player logs off.
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerRenderSettingsManager.onPlayerQuit(event.getPlayer());
        pathRendererManager.stopRendering(event.getPlayer());
    }

    private void registerCommands() {
        getCommand("trailblazer").setExecutor(new TrailblazerCommand(this));
        getCommand("trailblazer").setTabCompleter(new PathTabCompleter(pathDataManager));
        pluginLogger.info("Commands registered.");
    }

    public PathDataManager getPathDataManager() {
        return pathDataManager;
    }

    public PathRendererManager getPathRendererManager() {
        return pathRendererManager;
    }

    public PlayerRenderSettingsManager getPlayerRenderSettingsManager() {
        return playerRenderSettingsManager;
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