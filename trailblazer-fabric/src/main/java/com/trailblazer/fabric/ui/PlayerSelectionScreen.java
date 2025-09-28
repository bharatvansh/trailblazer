package com.trailblazer.fabric.ui;

import com.trailblazer.api.PathData;
import com.trailblazer.fabric.sharing.PathShareSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerSelectionScreen extends Screen {
    private final PathData path;
    private final Screen parent;
    private final List<PlayerListEntry> onlinePlayers;
    private final List<UUID> selectedPlayers = new ArrayList<>();
    private ButtonWidget shareButton;

    public PlayerSelectionScreen(PathData path, Screen parent) {
        super(Text.of("Share Path with Players"));
        this.path = path;
        this.parent = parent;
        var handler = MinecraftClient.getInstance().getNetworkHandler();
        this.onlinePlayers = handler != null ? new ArrayList<>(handler.getPlayerList()) : new ArrayList<>();
    }

    @Override
    protected void init() {
        super.init();

        int buttonWidth = 200;
        int buttonHeight = 20;
        int buttonX = this.width / 2 - buttonWidth / 2;
        int y = Math.max(40, this.height / 2 - (onlinePlayers.size() * (buttonHeight + 5)) / 2);

        for (PlayerListEntry playerEntry : onlinePlayers) {
            UUID playerUUID = playerEntry.getProfile().getId();
            ButtonWidget playerButton = ButtonWidget.builder(Text.of(playerEntry.getProfile().getName()), button -> {
                if (selectedPlayers.contains(playerUUID)) {
                    selectedPlayers.remove(playerUUID);
                    button.setMessage(Text.of(playerEntry.getProfile().getName()));
                } else {
                    selectedPlayers.add(playerUUID);
                    button.setMessage(Text.of("[X] " + playerEntry.getProfile().getName()));
                }
                updateShareState();
            }).dimensions(buttonX, y, buttonWidth, buttonHeight).build();
            this.addDrawableChild(playerButton);
            y += buttonHeight + 5;
        }

        if (onlinePlayers.isEmpty()) {
            this.addDrawableChild(ButtonWidget.builder(Text.of("No players online"), button -> {})
                .dimensions(buttonX, y, buttonWidth, buttonHeight)
                .build()).active = false;
        }

        shareButton = ButtonWidget.builder(Text.of("Share"), button -> {
            if (!selectedPlayers.isEmpty()) {
                PathShareSender.sharePath(path, selectedPlayers);
            }
            this.client.setScreen(parent);
        }).dimensions(buttonX, this.height - 65, buttonWidth, buttonHeight).build();
        shareButton.active = !selectedPlayers.isEmpty() && !onlinePlayers.isEmpty();
        this.addDrawableChild(shareButton);

        this.addDrawableChild(ButtonWidget.builder(Text.of("Cancel"), button -> this.client.setScreen(parent))
            .dimensions(buttonX, this.height - 40, buttonWidth, buttonHeight).build());
    }

    private void updateShareState() {
        if (shareButton != null) {
            shareButton.active = !selectedPlayers.isEmpty();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
    }
}
