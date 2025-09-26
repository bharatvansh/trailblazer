package com.trailblazer.fabric.ui;

import com.trailblazer.api.PathData;
import com.trailblazer.fabric.ClientPathManager;
import com.trailblazer.fabric.ClientPathManager.PathOrigin;
import com.trailblazer.fabric.ServerIntegrationBridge;
import com.trailblazer.fabric.networking.payload.c2s.UpdatePathMetadataPayload;
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
        super(client, width, height, top, Math.max(itemHeight, 38));
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
    private final PathOrigin origin;
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
        this.origin = pathManager.getPathOrigin(path.getPathId());

            this.toggleButton = ButtonWidget.builder(getToggleButtonText(), button -> {
                pathManager.togglePathVisibility(path.getPathId());
                button.setMessage(getToggleButtonText());
            }).build();


            this.shareButton = ButtonWidget.builder(Text.of("Share"), button -> {
                MinecraftClient.getInstance().setScreen(new PlayerSelectionScreen(path, MinecraftClient.getInstance().currentScreen));
            }).build();

            boolean serverAvailable = ServerIntegrationBridge.SERVER_INTEGRATION != null && ServerIntegrationBridge.SERVER_INTEGRATION.isServerSupported();
            if (origin == PathOrigin.LOCAL) {
                this.shareButton.active = serverAvailable;
            } else if (origin == PathOrigin.SERVER_OWNED) {
                this.shareButton.active = serverAvailable;
            } else {
                this.shareButton.active = false;
            }

            this.editButton = ButtonWidget.builder(Text.of("Edit"), button -> {
                MinecraftClient.getInstance().setScreen(new PathCreationScreen(pathManager, updatedPath -> {
                    pathManager.onPathUpdated(updatedPath);
                    if (origin == PathOrigin.SERVER_OWNED && ClientPlayNetworking.canSend(UpdatePathMetadataPayload.ID)) {
                        ClientPlayNetworking.send(new UpdatePathMetadataPayload(
                                updatedPath.getPathId(),
                                updatedPath.getPathName(),
                                updatedPath.getColorArgb()
                        ));
                    }
                }, path));
            }).build();
            if (origin == PathOrigin.SERVER_SHARED) {
                this.editButton.active = false;
                this.editButton.setMessage(Text.of("Edit (Owner)"));
            }

            this.deleteButton = ButtonWidget.builder(Text.of("Delete"), button -> {
                long now = System.currentTimeMillis();
                if (!awaitingDeleteConfirm || now - deleteConfirmStartMs > CONFIRM_TIMEOUT_MS) {
                    awaitingDeleteConfirm = true;
                    deleteConfirmStartMs = now;
                    button.setMessage(Text.of("Confirm"));
                    return;
                }
                // Second click within window -> perform deletion
                if (origin == PathOrigin.SERVER_SHARED) {
                    pathManager.removeSharedPath(path.getPathId());
                } else {
                    if (origin == PathOrigin.LOCAL) {
                        pathManager.deletePath(path.getPathId());
                    } else if (origin == PathOrigin.SERVER_OWNED && ClientPlayNetworking.canSend(DeletePathPayload.ID)) {
                        ClientPlayNetworking.send(new DeletePathPayload(path.getPathId()));
                    }
                }
            }).build();
            if (origin == PathOrigin.SERVER_SHARED) {
                this.deleteButton.setMessage(Text.of("Remove"));
            }

        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int baseX = x + 5;
            int textY = y + 4; // slightly less top padding to compact the entry vertically
            if (origin != null) {
                int badgeColor = originBadgeColor();
                int squareX = baseX;
                int squareY = textY;
                context.fill(squareX, squareY, squareX + 8, squareY + 8, badgeColor);

                if (mouseX >= squareX && mouseX <= squareX + 8 && mouseY >= squareY && mouseY <= squareY + 8) {
                    context.drawTooltip(MinecraftClient.getInstance().textRenderer, getOriginTooltipText(), mouseX, mouseY);
                }
                baseX += 12;
            }
            // Calculate available width for text (subtract button area width)
            int buttonAreaWidth = isMyPath ? 340 : 170; // 4 buttons vs 2 buttons (85px each + spacing)
            int availableTextWidth = entryWidth - (baseX - x) - buttonAreaWidth - 10; // 10px padding
            
            // Trim path name if it's too long
            var textRenderer = MinecraftClient.getInstance().textRenderer;
            String displayName = path.getPathName();
            if (textRenderer.getWidth(displayName) > availableTextWidth) {
                displayName = textRenderer.trimToWidth(displayName, availableTextWidth - textRenderer.getWidth("...")) + "...";
            }
            
            context.drawTextWithShadow(textRenderer, displayName, baseX, textY, 0xFFFFFF);
            if (!isMyPath) {
                String ownerText = " (by " + path.getOwnerName() + ")";
                int ownerX = baseX + textRenderer.getWidth(displayName);
                // Make sure owner text doesn't overflow either
                int remainingWidth = availableTextWidth - textRenderer.getWidth(displayName);
                if (textRenderer.getWidth(ownerText) > remainingWidth) {
                    ownerText = textRenderer.trimToWidth(ownerText, remainingWidth - textRenderer.getWidth("...")) + "...";
                }
                context.drawTextWithShadow(textRenderer, ownerText, ownerX, textY, 0xAAAAAA);
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

            // Coordinates row below buttons (20px button height + padding)
            int coordY = y + 22; // below buttons
            int contentRightX = x + entryWidth - 10; // align with list padding
            drawCoordinates(context, baseX, contentRightX, coordY, textRenderer);

            // Draw separator line at the bottom of the entry
            int separatorY = y + entryHeight - 1;
            context.fill(x, separatorY, x + entryWidth, separatorY + 1, 0xFF444444); // Dark gray separator

            // Reset confirm state if timeout elapsed (and user didn't click second time)
            if (awaitingDeleteConfirm && System.currentTimeMillis() - deleteConfirmStartMs > CONFIRM_TIMEOUT_MS) {
                awaitingDeleteConfirm = false;
                deleteButton.setMessage(origin == PathOrigin.SERVER_SHARED ? Text.of("Remove") : Text.of("Delete"));
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

        private Text getOriginTooltipText() {
            return switch (origin) {
                case LOCAL -> Text.of("Stored in Client");
                case SERVER_OWNED, SERVER_SHARED -> Text.of("Stored in Server");
            };
        }

        private int originBadgeColor() {
            return switch (origin) {
                case LOCAL -> 0xFF55FF55; // Green
                case SERVER_OWNED, SERVER_SHARED -> 0xFF55AAFF; // Blue
            };
        }

        private void drawCoordinates(DrawContext context, int baseX, int contentRightX, int yCoord, net.minecraft.client.font.TextRenderer tr) {
            if (path.getPoints().isEmpty()) return;
            var startPoint = path.getPoints().get(0);
            var endPoint = path.getPoints().get(path.getPoints().size() - 1);
            String startText = String.format("Start: %.0f, %.0f, %.0f", startPoint.getX(), startPoint.getY(), startPoint.getZ());
            String endText = String.format("End: %.0f, %.0f, %.0f", endPoint.getX(), endPoint.getY(), endPoint.getZ());
            if (contentRightX <= baseX) return;
            int maxWidth = contentRightX - baseX;
            if (maxWidth <= 70) return;

            String arrow = "→";
            int arrowWidth = tr.getWidth(arrow);
            int endWidth = tr.getWidth(endText);
            int availableForStart = maxWidth - endWidth - arrowWidth - 16; // minimum spacing between segments
            if (availableForStart < 40) return;

            if (tr.getWidth(startText) > availableForStart) {
                startText = tr.trimToWidth(startText, Math.max(availableForStart - tr.getWidth("..."), 0)) + "...";
            }

            int startWidth = tr.getWidth(startText);
            int startX = baseX;
            int endX = contentRightX - endWidth;

            // center the arrow when possible while preventing overlap with start/end text
            int arrowX = baseX + (maxWidth - arrowWidth) / 2;
            int minArrowX = startX + startWidth + 4;
            int maxArrowX = endX - arrowWidth - 4;
            if (arrowX < minArrowX) {
                arrowX = minArrowX;
            } else if (arrowX > maxArrowX) {
                arrowX = maxArrowX;
            }

            context.drawTextWithShadow(tr, startText, startX, yCoord, 0xBBBBBB);
            context.drawTextWithShadow(tr, arrow, arrowX, yCoord, 0x888888);
            context.drawTextWithShadow(tr, endText, endX, yCoord, 0xBBBBBB);
        }
    }
}
