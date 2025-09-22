package com.trailblazer.fabric;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.trailblazer.fabric.networking.ClientPacketHandler;
import com.trailblazer.fabric.networking.TrailblazerNetworking;
import com.trailblazer.fabric.networking.payload.c2s.HandshakePayload;
import com.trailblazer.fabric.rendering.PathRenderer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class TrailblazerFabricClient implements ClientModInitializer {

    public static final String MOD_ID = "trailblazer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // --- NEW INSTANCES ---
    private ClientPathManager clientPathManager;
    private PathRenderer pathRenderer;
    private KeyBindingManager keyBindingManager;
    // --- END NEW ---

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Trailblazer client...");

        // --- INITIALIZATION LOGIC ---
        this.clientPathManager = new ClientPathManager();
        this.pathRenderer = new PathRenderer(clientPathManager);
        this.keyBindingManager = new KeyBindingManager();

        // Register the renderer with the game's render events
        pathRenderer.initialize();
        keyBindingManager.initialize();

        // Register payload codecs before registering handlers.
        TrailblazerNetworking.registerPayloadTypes();
        ClientPacketHandler.registerS2CPackets(clientPathManager);

        registerHandshakeSender();

        LOGGER.info("Trailblazer client initialized.");
    }

    private void registerHandshakeSender() {
        // This event fires when the client successfully joins a server world.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // We schedule this with a small delay to ensure the server is ready for our packet.
            client.execute(() -> {
                if (ClientPlayNetworking.canSend(HandshakePayload.ID)) {
                    LOGGER.info("Sending Trailblazer handshake to server...");
                    ClientPlayNetworking.send(new HandshakePayload());
                }
            });
        });
    }
}