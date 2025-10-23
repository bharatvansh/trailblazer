package com.trailblazer.fabric.ui;

import com.trailblazer.api.PathData;
import com.trailblazer.fabric.sharing.PathShareSender;
import com.trailblazer.fabric.networking.payload.c2s.SharePathRequestPayload;
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
    private final net.minecraft.text.Text shareDisabledTooltipPlugin = Text.of("Server-side plugin required");
    private final net.minecraft.text.Text shareDisabledTooltipSelection = Text.of("Select at least one player");
    private final net.minecraft.text.Text shareDisabledTooltipNoPlayers = Text.of("No players online");

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

        boolean canShare = net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(SharePathRequestPayload.ID);
        shareButton = ButtonWidget.builder(Text.of("Share"), button -> {
            if (!selectedPlayers.isEmpty()) {
                PathShareSender.sharePath(path, selectedPlayers);
            }
            this.client.setScreen(parent);
        }).dimensions(buttonX, this.height - 65, buttonWidth, buttonHeight).build();
        shareButton.active = canShare && !selectedPlayers.isEmpty() && !onlinePlayers.isEmpty();
        this.addDrawableChild(shareButton);

        this.addDrawableChild(ButtonWidget.builder(Text.of("Cancel"), button -> this.client.setScreen(parent))
            .dimensions(buttonX, this.height - 40, buttonWidth, buttonHeight).build());
    }

    private void updateShareState() {
        if (shareButton != null) {
            boolean canShare = net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(SharePathRequestPayload.ID);
            shareButton.active = canShare && !selectedPlayers.isEmpty() && !onlinePlayers.isEmpty();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Avoid triggering the vanilla blur twice in a single frame.
        // We draw a light translucent backdrop ourselves (same pattern as MainMenuScreen)
        // to keep readability when opened over the in-game world.
        context.fill(0, 0, this.width, this.height, 0x26000000);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        // tooltip for disabled share button when no server-side support
        if (!shareButton.active) {
            int sx = shareButton.getX();
            int sy = shareButton.getY();
            int sw = shareButton.getWidth();
            int sh = shareButton.getHeight();
            if (mouseX >= sx && mouseX <= sx + sw && mouseY >= sy && mouseY <= sy + sh) {
                boolean canShare = net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(SharePathRequestPayload.ID);
                net.minecraft.text.Text tip = !canShare
                        ? shareDisabledTooltipPlugin
                        : (onlinePlayers.isEmpty() ? shareDisabledTooltipNoPlayers : shareDisabledTooltipSelection);
                context.drawTooltip(this.textRenderer, tip, mouseX, mouseY);
            }
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Only invoke the vanilla background (which applies a per-frame blur) when not in a world.
        // When in-game, we rely on our own translucent fill to avoid the "Can only blur once per frame" crash
        // that occurs when two screens request a blur during the same frame.
        if (this.client == null || this.client.world == null) {
            super.renderBackground(context, mouseX, mouseY, delta);
        }
    }
}
