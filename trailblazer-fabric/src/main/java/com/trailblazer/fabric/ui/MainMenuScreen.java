package com.trailblazer.fabric.ui;

import com.trailblazer.fabric.ClientPathManager;
import com.trailblazer.fabric.RenderSettingsManager;
import com.trailblazer.api.PathData;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class MainMenuScreen extends Screen {
    private final ClientPathManager pathManager;
    private final RenderSettingsManager renderSettingsManager;
    private ButtonWidget myPathsTab;
    private ButtonWidget sharedWithMeTab;
    private ButtonWidget settingsButton;
    private ButtonWidget recordButton;
    private PathListWidget pathListWidget;
    private int lastPathCount = -1;

    private boolean showingMyPaths = true;

    public MainMenuScreen(ClientPathManager pathManager, RenderSettingsManager renderSettingsManager) {
        super(Text.of("Trailblazer Main Menu"));
        this.pathManager = pathManager;
        this.renderSettingsManager = renderSettingsManager;
    }

    @Override
    protected void init() {
        super.init();

        int tabWidth = 100;
        int tabHeight = 20;
        int tabY = 30;

        myPathsTab = ButtonWidget.builder(Text.of("My Paths"), button -> {
            showingMyPaths = true;
            updatePathList();
        }).dimensions(this.width / 2 - tabWidth - 5, tabY, tabWidth, tabHeight).build();

        sharedWithMeTab = ButtonWidget.builder(Text.of("Shared With Me"), button -> {
            showingMyPaths = false;
            updatePathList();
        }).dimensions(this.width / 2 + 5, tabY, tabWidth, tabHeight).build();

        this.addDrawableChild(myPathsTab);
        this.addDrawableChild(sharedWithMeTab);

        settingsButton = ButtonWidget.builder(Text.of("Settings"), button -> {
            this.client.setScreen(new SettingsScreen(renderSettingsManager, this));
        }).dimensions(this.width - 105, 5, 100, 20).build();
        this.addDrawableChild(settingsButton);

        recordButton = ButtonWidget.builder(Text.of(getRecordingLabel()), button -> {
            boolean starting = !pathManager.isRecording();
            if (starting) {
                pathManager.startRecordingLocal();
            } else {
                pathManager.stopRecordingLocal();
            }
            ClientPlayNetworking.send(new com.trailblazer.fabric.networking.payload.c2s.ToggleRecordingPayload());
            // Update label immediately
            recordButton.setMessage(Text.of(getRecordingLabel()));
            if (starting) {
                // Close menu so recording can proceed unobstructed
                this.client.setScreen(null);
            }
        }).dimensions(5, 5, 110, 20).build();
        this.addDrawableChild(recordButton);

        pathListWidget = new PathListWidget(this.client, this.width, this.height - 80, 60, 20);
        this.addDrawableChild(pathListWidget);

        updatePathList();
    }

    private void updatePathList() {
        pathListWidget.clearEntries();
        List<PathData> paths = new ArrayList<>(showingMyPaths ? pathManager.getMyPaths() : pathManager.getSharedPaths());
        for (PathData path : paths) {
            pathListWidget.addEntry(pathListWidget.new PathEntry(path, pathManager, showingMyPaths));
        }
        lastPathCount = paths.size();
        // Also refresh record button label in case state changed externally
        if (recordButton != null) {
            recordButton.setMessage(Text.of(getRecordingLabel()));
        }
    }

    private String getRecordingLabel() {
        return pathManager.isRecording() ? "Stop Recording" : "Start Recording";
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // DEBUG: remove any in-game dim entirely to isolate list tint sources
        if (this.client == null || this.client.world == null) {
            super.renderBackground(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public void tick() {
        super.tick();
        int currentCount = (showingMyPaths ? pathManager.getMyPaths().size() : pathManager.getSharedPaths().size());
        if (currentCount != lastPathCount) {
            updatePathList();
        }
        // Ensure label stays in sync with possible keybind toggles
        if (recordButton != null) {
            String desired = getRecordingLabel();
            if (!recordButton.getMessage().getString().equals(desired)) {
                recordButton.setMessage(Text.of(desired));
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        // Full-screen dim: darker (~15% alpha). Rows (31-35%) remain clearly on top.
        context.fill(0, 0, this.width, this.height, 0x26000000);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);
    }
}

