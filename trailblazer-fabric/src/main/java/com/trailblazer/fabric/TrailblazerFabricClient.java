package com.trailblazer.fabric;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.trailblazer.fabric.networking.ClientPacketHandler;
import com.trailblazer.fabric.networking.TrailblazerNetworking;
import com.trailblazer.fabric.networking.payload.c2s.HandshakePayload;
import com.trailblazer.fabric.rendering.PathRenderer;
import com.trailblazer.fabric.ui.MainMenuScreen;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import org.lwjgl.glfw.GLFW;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public class TrailblazerFabricClient implements ClientModInitializer {

    public static final String MOD_ID = "trailblazer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private ClientPathManager clientPathManager;
    private PathRenderer pathRenderer;
    private RenderSettingsManager renderSettingsManager;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Trailblazer client...");

        this.clientPathManager = new ClientPathManager();
        this.renderSettingsManager = new RenderSettingsManager();
        this.pathRenderer = new PathRenderer(clientPathManager, renderSettingsManager);

        pathRenderer.initialize();
        KeyBindingManager.initialize(renderSettingsManager, clientPathManager);        TrailblazerNetworking.registerPayloadTypes();
        ClientPacketHandler.registerS2CPackets(clientPathManager);

        registerHandshakeSender();

        LOGGER.info("Trailblazer client initialized.");
    }

    private void registerHandshakeSender() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            client.execute(() -> {
                if (ClientPlayNetworking.canSend(HandshakePayload.ID)) {
                    LOGGER.info("Sending Trailblazer handshake to server...");
                    ClientPlayNetworking.send(new HandshakePayload());
                }
            });
        });
    }
}