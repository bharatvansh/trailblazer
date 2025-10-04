package com.trailblazer.fabric.ui;

import com.trailblazer.api.PathData;
import com.trailblazer.fabric.ClientPathManager;
import com.trailblazer.fabric.ClientPathManager.PathOrigin;
import com.trailblazer.fabric.RenderSettingsManager;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.trailblazer.fabric.networking.payload.c2s.SharePathRequestPayload;

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

        recordButton = ButtonWidget.builder(getRecordingText(), button -> {
            boolean isRecording = pathManager.isRecording();

            if (isRecording) {
                pathManager.stopRecordingLocal();
            } else {
                pathManager.startRecordingLocal();
            }

            recordButton.setMessage(getRecordingText());
            if (!isRecording) {
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
        List<PathData> paths = new ArrayList<>();
        if (showingMyPaths) {
            for (PathData path : pathManager.getMyPaths()) {
                PathOrigin origin = pathManager.getPathOrigin(path.getPathId());
                if (origin == PathOrigin.LOCAL || origin == PathOrigin.SERVER_OWNED) {
                    paths.add(path);
                }
            }
        } else {
            Set<java.util.UUID> seen = new HashSet<>();
            for (PathData path : pathManager.getMyPaths()) {
                PathOrigin origin = pathManager.getPathOrigin(path.getPathId());
                if (origin == PathOrigin.IMPORTED && seen.add(path.getPathId())) {
                    paths.add(path);
                }
            }
            for (PathData path : pathManager.getSharedPaths()) {
                PathOrigin origin = pathManager.getPathOrigin(path.getPathId());
                if (origin == PathOrigin.SERVER_SHARED && seen.add(path.getPathId())) {
                    paths.add(path);
                }
            }
        }
        for (PathData path : paths) {
            pathListWidget.addEntry(pathListWidget.new PathEntry(path, pathManager, showingMyPaths));
        }
        lastPathCount = paths.size();
        if (recordButton != null) {
            recordButton.setMessage(getRecordingText());
        }
        refreshTabState();
    }

    private void refreshTabState() {
        if (myPathsTab != null) {
            myPathsTab.active = !showingMyPaths;
        }
        if (sharedWithMeTab != null) {
            sharedWithMeTab.active = showingMyPaths;
        }
    }

    private Text getRecordingText() {
        if (pathManager.isRecording()) {
            return Text.literal("Stop Recording").formatted(Formatting.RED);
        }
        return Text.literal("Start Recording");
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
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
        if (recordButton != null) {
            Text desired = getRecordingText();
            if (!recordButton.getMessage().getString().equals(desired.getString())) {
                recordButton.setMessage(desired);
            }
        }
        refreshTabState();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.fill(0, 0, this.width, this.height, 0x26000000);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);

        // When the user is viewing the Shared With Me tab but sharing is not available
        if (!showingMyPaths) {
            boolean canShare = ClientPlayNetworking.canSend(SharePathRequestPayload.ID);
            boolean hasShared = !pathManager.getSharedPaths().isEmpty();
            if (!canShare && !hasShared) {
                String[] lines = new String[] {
                    "Server-side plugin required",
                    "",
                    "The sharing features require the Trailblazer server-side plugin to be installed.",
                    "This is not available in singleplayer or when connecting to an unmodded server.",
                    "Install the Trailblazer plugin on the server to enable sharing between players."
                };
                int boxWidth = Math.min(this.width - 60, 800);
                int boxX = (this.width - boxWidth) / 2;
                int boxY = this.height / 2 - 60;
                int lineHeight = this.textRenderer.fontHeight + 4;
                int boxHeight = lines.length * lineHeight + 12;
                // background
                context.fill(boxX - 8, boxY - 6, boxX + boxWidth + 8, boxY + boxHeight + 6, 0x90000000);
                // draw lines centered
                for (int i = 0; i < lines.length; i++) {
                    String s = lines[i];
                    int y = boxY + i * lineHeight;
                    context.drawCenteredTextWithShadow(this.textRenderer, Text.of(s), this.width / 2, y, i == 0 ? 0xFFFF5555 : 0xFFFFFF);
                }
            } else if (canShare && !hasShared) {
                // Server is present but there are no shared paths yet — show a friendly, subtle message
                String[] lines = new String[] {
                    "No shared paths yet",
                    "",
                    "No one has shared any paths with you on this server yet.",
                    "Ask other players to share a path, or share one of your own."
                };
                int boxWidth = Math.min(this.width - 60, 700);
                int boxX = (this.width - boxWidth) / 2;
                int boxY = this.height / 2 - 40;
                int lineHeight = this.textRenderer.fontHeight + 4;
                int boxHeight = lines.length * lineHeight + 12;
                context.fill(boxX - 8, boxY - 6, boxX + boxWidth + 8, boxY + boxHeight + 6, 0x90000000);
                for (int i = 0; i < lines.length; i++) {
                    String s = lines[i];
                    int y = boxY + i * lineHeight;
                    context.drawCenteredTextWithShadow(this.textRenderer, Text.of(s), this.width / 2, y, i == 0 ? 0xFFFFFF : 0xDDDDDD);
                }
            }
        }
    }
}

