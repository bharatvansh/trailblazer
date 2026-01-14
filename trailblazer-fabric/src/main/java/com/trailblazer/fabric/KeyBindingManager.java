package com.trailblazer.fabric;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public class KeyBindingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(KeyBindingManager.class);

    private static final KeyBinding.Category KEY_CATEGORY = KeyBinding.Category.create(Identifier.of("trailblazer", "trailblazer"));

    private static KeyBinding toggleRecordingKey;
    private static KeyBinding cycleRenderModeKey;
    private static KeyBinding openMenuKey;

    public static void initialize(RenderSettingsManager renderSettingsManager, ClientPathManager clientPathManager) {
        toggleRecordingKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.trailblazer.toggle_recording",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                KEY_CATEGORY));

        cycleRenderModeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.trailblazer.cycle_render_mode",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                KEY_CATEGORY));
        
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.trailblazer.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                KEY_CATEGORY));

        registerKeyListeners(renderSettingsManager, clientPathManager);
    }

    private static void registerKeyListeners(RenderSettingsManager renderSettingsManager, ClientPathManager clientPathManager) {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleRecordingKey.wasPressed()) {
                if (client.player != null) {
                    boolean isRecording = clientPathManager.isRecording();
                    boolean useServer = clientPathManager.shouldUseServerRecording();
                    
                    LOGGER.info("Recording key (R) pressed: isRecording={}, useServerRecording={}", isRecording, useServer);

                    if (isRecording) {
                        if (useServer) {
                            clientPathManager.sendStopRecordingRequest(true);
                            client.player.sendMessage(Text.literal("Stopping server-side recording...").formatted(Formatting.GREEN), true);
                        } else {
                            clientPathManager.stopRecordingLocal();
                            client.player.sendMessage(Text.literal("Stopped local recording.").formatted(Formatting.GREEN), true);
                        }
                    } else {
                        if (useServer) {
                            LOGGER.info("Keybinding: Using SERVER recording");
                            clientPathManager.sendStartRecordingRequest(null);
                            client.player.sendMessage(Text.literal("Started server-side recording.").formatted(Formatting.GREEN), true);
                        } else {
                            LOGGER.info("Keybinding: Using LOCAL recording");
                            clientPathManager.startRecordingLocal();
                            client.player.sendMessage(Text.literal("Started local recording.").formatted(Formatting.GREEN), true);
                        }
                    }
                }
            }

            while (cycleRenderModeKey.wasPressed()) {
                if (client.player != null) {
                    LOGGER.info("Cycle render mode key pressed.");
                    renderSettingsManager.cycleRenderMode();
                }
            }

            while (openMenuKey.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new com.trailblazer.fabric.ui.MainMenuScreen(clientPathManager, renderSettingsManager));
                }
            }
        });
    }
}

