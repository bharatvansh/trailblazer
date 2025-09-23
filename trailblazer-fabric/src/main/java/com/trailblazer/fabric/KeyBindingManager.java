package com.trailblazer.fabric;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.trailblazer.fabric.networking.payload.c2s.ToggleRecordingPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class KeyBindingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(KeyBindingManager.class);

    private static final String KEY_CATEGORY = "key.categories.trailblazer";

    private static KeyBinding toggleRecordingKey;
    // --- NEW KEYBINDING ---
    private static KeyBinding cycleRenderModeKey;

    public static void initialize(RenderSettingsManager renderSettingsManager) {
        // --- END NEW ---
        toggleRecordingKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.trailblazer.toggle_recording",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                KEY_CATEGORY));

        // --- NEW KEYBINDING REGISTRATION ---
        cycleRenderModeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.trailblazer.cycle_render_mode",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                KEY_CATEGORY));
        // --- END NEW ---

        registerKeyListeners(renderSettingsManager);
    }

    private static void registerKeyListeners(RenderSettingsManager renderSettingsManager) {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Handle the toggle recording key
            while (toggleRecordingKey.wasPressed()) {
                if (client.player != null) {
                    LOGGER.info("Toggle recording key pressed. Sending packet to server.");
                    client.player.sendMessage(Text.literal("Toggling path recording...").formatted(Formatting.YELLOW), true);
                    ClientPlayNetworking.send(new ToggleRecordingPayload());
                }
            }

            // --- NEW KEY LISTENER ---
            // Handle the cycle render mode key
            while (cycleRenderModeKey.wasPressed()) {
                if (client.player != null) {
                    LOGGER.info("Cycle render mode key pressed.");
                    renderSettingsManager.cycleRenderMode();
                }
            }
            // --- END NEW ---
        });
    }
}

