package com.trailblazer.fabric.ui;

import com.trailblazer.api.PathData;
import com.trailblazer.fabric.ClientPathManager;
import com.trailblazer.fabric.networking.payload.c2s.SharePathPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.trailblazer.fabric.networking.payload.c2s.DeletePathPayload;
import java.util.List;

public class PathListWidget extends ElementListWidget<PathListWidget.PathEntry> {

    public PathListWidget(MinecraftClient client, int width, int height, int top, int itemHeight) {
        super(client, width, height, top, itemHeight);
    }

    @Override
    public int getRowWidth() {
        return this.width - 20;
    }

    public void clearEntries() {
        super.clearEntries();
    }

    public int addEntry(PathEntry entry) {
        return super.addEntry(entry);
    }

    public static class PathEntry extends ElementListWidget.Entry<PathEntry> {
        private final PathData path;
        private final ClientPathManager pathManager;
        private final boolean isMyPath;
    private final ButtonWidget toggleButton;
        private final ButtonWidget shareButton;
        private final ButtonWidget editButton;
        private final ButtonWidget deleteButton;
    private boolean awaitingDeleteConfirm = false;
    private long deleteConfirmStartMs = 0L;
    private static final long CONFIRM_TIMEOUT_MS = 5000L;

        public PathEntry(PathData path, ClientPathManager pathManager, boolean isMyPath) {
            this.path = path;
            this.pathManager = pathManager;
            this.isMyPath = isMyPath;

            this.toggleButton = ButtonWidget.builder(getToggleButtonText(), button -> {
                pathManager.togglePathVisibility(path.getPathId());
                button.setMessage(getToggleButtonText());
            }).build();


            this.shareButton = ButtonWidget.builder(Text.of("Share"), button -> {
                ClientPlayNetworking.send(new SharePathPayload(path.getPathId()));
            }).build();

            this.editButton = ButtonWidget.builder(Text.of("Edit"), button -> {
                MinecraftClient.getInstance().setScreen(new PathCreationScreen(pathManager, updatedPath -> {
                    // No action needed here, the path is updated in the creation screen
                }, path));
            }).build();

            this.deleteButton = ButtonWidget.builder(Text.of("Delete"), button -> {
                long now = System.currentTimeMillis();
                if (!awaitingDeleteConfirm || now - deleteConfirmStartMs > CONFIRM_TIMEOUT_MS) {
                    awaitingDeleteConfirm = true;
                    deleteConfirmStartMs = now;
                    button.setMessage(Text.of("Confirm"));
                    return;
                }
                // Second click within window -> perform deletion
                if (isMyPath) {
                    // Optimistic local removal
                    pathManager.deletePath(path.getPathId());
                    if (ClientPlayNetworking.canSend(DeletePathPayload.ID)) {
                        ClientPlayNetworking.send(new DeletePathPayload(path.getPathId()));
                    }
                } else {
                    pathManager.removeSharedPath(path.getPathId());
                }
            }).build();

        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, path.getPathName(), x + 5, y + 5, 0xFFFFFF);
            if (!isMyPath) {
                context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, " (by " + path.getOwnerName() + ")", x + 5 + MinecraftClient.getInstance().textRenderer.getWidth(path.getPathName()), y + 5, 0xAAAAAA);
            }

            int buttonX = x + entryWidth - 80;
            int buttonY = y;
            int buttonWidth = 75;
            int buttonHeight = 20;

            toggleButton.setX(buttonX);
            toggleButton.setY(buttonY);
            toggleButton.setWidth(buttonWidth);
            toggleButton.setHeight(buttonHeight);
            toggleButton.render(context, mouseX, mouseY, tickDelta);

            if (isMyPath) {
                buttonX -= 85;
                shareButton.setX(buttonX);
                shareButton.setY(buttonY);
                shareButton.setWidth(buttonWidth);
                shareButton.setHeight(buttonHeight);
                shareButton.render(context, mouseX, mouseY, tickDelta);

                buttonX -= 85;
                editButton.setX(buttonX);
                editButton.setY(buttonY);
                editButton.setWidth(buttonWidth);
                editButton.setHeight(buttonHeight);
                editButton.render(context, mouseX, mouseY, tickDelta);
            }

            buttonX -= 85;
            deleteButton.setX(buttonX);
            deleteButton.setY(buttonY);
            deleteButton.setWidth(buttonWidth);
            deleteButton.setHeight(buttonHeight);
            deleteButton.render(context, mouseX, mouseY, tickDelta);

            // Reset confirm state if timeout elapsed (and user didn't click second time)
            if (awaitingDeleteConfirm && System.currentTimeMillis() - deleteConfirmStartMs > CONFIRM_TIMEOUT_MS) {
                awaitingDeleteConfirm = false;
                deleteButton.setMessage(Text.of("Delete"));
            }
        }

        private Text getToggleButtonText() {
            boolean isVisible = pathManager.isPathVisible(path.getPathId());
            return Text.of("Toggle: " + (isVisible ? "ON" : "OFF"))
                .copy()
                .formatted(isVisible ? Formatting.GREEN : Formatting.RED);
        }

        @Override
        public List<? extends net.minecraft.client.gui.Element> children() {
            if (isMyPath) {
                return List.of(toggleButton, shareButton, editButton, deleteButton);
            }
            return List.of(toggleButton, deleteButton);
        }

        @Override
        public List<? extends net.minecraft.client.gui.Selectable> selectableChildren() {
            if (isMyPath) {
                return List.of(toggleButton, shareButton, editButton, deleteButton);
            }
            return List.of(toggleButton, deleteButton);
        }
    }
}
