package com.trailblazer.fabric;

import com.trailblazer.fabric.rendering.RenderMode;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;

/**
 * Manages client-side path rendering settings.
 */
public class RenderSettingsManager {

    private RenderMode currentMode = RenderMode.PARTICLE_TRAIL;
    private double markerSpacing = 3.0;

    public RenderMode getRenderMode() {
        return currentMode;
    }

    public void setRenderMode(RenderMode mode) {
        if (mode == null || mode == this.currentMode) {
            return;
        }
        this.currentMode = mode;
        notifyModeChanged();
    }

    public double getMarkerSpacing() {
        return markerSpacing;
    }

    public void cycleRenderMode() {
        currentMode = currentMode.next();
        notifyModeChanged();
    }

    private void notifyModeChanged() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            Text title = Text.literal("Render Mode");
            Text description = currentMode.getDisplayText();
            client.getToastManager().add(new SystemToast(SystemToast.Type.PERIODIC_NOTIFICATION, title, description));
        }
    }
}