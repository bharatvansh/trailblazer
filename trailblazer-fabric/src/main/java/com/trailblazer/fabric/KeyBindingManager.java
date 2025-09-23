package com.trailblazer.fabric;

import org.lwjgl.glfw.GLFW;

import com.trailblazer.fabric.networking.payload.c2s.ToggleRecordingPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeyBindingManager {

    public static final Logger LOGGER = LoggerFactory.getLogger("TrailblazerKeybind");
    private static KeyBinding toggleRecordingKey;

    public static void initialize() {
        LOGGER.info("Initializing KeyBindingManager...");
        toggleRecordingKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.trailblazer.toggle_recording",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "category.trailblazer.main"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (toggleRecordingKey.wasPressed()) {
                LOGGER.info("Toggle recording key was pressed.");
                if (client.player != null) {
                    if (ClientPlayNetworking.canSend(ToggleRecordingPayload.ID)) {
                        LOGGER.info("Sending ToggleRecordingPayload to server...");
                        ClientPlayNetworking.send(new ToggleRecordingPayload());
                        LOGGER.info("ToggleRecordingPayload sent.");
                    } else {
                        LOGGER.warn("Cannot send ToggleRecordingPayload, channel not ready.");
                    }
                } else {
                    LOGGER.warn("Player is null, cannot send ToggleRecordingPayload.");
                }
            }
        });
        LOGGER.info("KeyBindingManager initialized.");
    }
}


