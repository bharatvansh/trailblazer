package com.trailblazer.fabric;

import com.trailblazer.fabric.rendering.RenderMode;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;

/**
 * Manages the client-side settings for how paths are rendered.
 */
public class RenderSettingsManager {

    private RenderMode currentMode = RenderMode.PARTICLE_TRAIL;
    private double markerSpacing = 3.0; // Default spacing of 3 blocks for markers/arrows

    /**
     * Gets the currently active render mode.
     *
     * @return The current RenderMode.
     */
    public RenderMode getCurrentMode() {
        return currentMode;
    }

    /**
     * Gets the spacing for markers and arrows.
     *
     * @return The spacing in blocks.
     */
    public double getMarkerSpacing() {
        return markerSpacing;
    }

    /**
     * Cycles to the next available render mode and notifies the player.
     */
    public void cycleRenderMode() {
        currentMode = currentMode.next();

        // Notify the player of the change
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            Text title = Text.literal("Render Mode");
            Text description = currentMode.getDisplayText();
            client.getToastManager().add(new SystemToast(SystemToast.Type.PERIODIC_NOTIFICATION, title, description));
        }
    }
}