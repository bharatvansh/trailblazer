package com.trailblazer.fabric.ui;

import com.trailblazer.fabric.ClientPathManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/** Simple HUD overlay for recording indicator. */
public class RecordingOverlay implements HudRenderCallback {
    private final ClientPathManager pathManager;
    public RecordingOverlay(ClientPathManager mgr) { this.pathManager = mgr; }

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        if (!pathManager.isRecording()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        var recordingPath = pathManager.getRecordingPath();
        String label = "Recording Path: " + (recordingPath != null ? recordingPath.getPathName() : "...") +
                " (" + (recordingPath != null ? recordingPath.getPoints().size() : 0) + ")";
        context.drawTextWithShadow(client.textRenderer, Text.literal(label), 8, 8 + client.textRenderer.fontHeight, 0xFFFF5555);
    }
}
