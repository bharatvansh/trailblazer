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
    // Container geometry captured from constructor params so we can render a proper background
    private final int containerTop;
    private final int containerHeight;

    public PathListWidget(MinecraftClient client, int width, int height, int top, int itemHeight) {
        super(client, width, height, top, Math.max(itemHeight, 36));
        this.containerTop = top;
        this.containerHeight = height;
        // Attempt to disable the list's built-in background so we can fully control opacity
        boolean disabledViaMethod = false;
        try {
            Class<?> c = net.minecraft.client.gui.widget.ElementListWidget.class;
            while (c != null && !disabledViaMethod) {
                for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals("setRenderBackground")
                            && m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == boolean.class) {
                        m.setAccessible(true);
                        m.invoke(this, false);
                        disabledViaMethod = true;
                        break;
                    }
                }
                c = c.getSuperclass();
            }
        } catch (Throwable t) {
            // ignore
        }
        if (!disabledViaMethod) {
            try {
                Class<?> c = net.minecraft.client.gui.widget.ElementListWidget.class;
                boolean fieldSet = false;
                while (c != null && !fieldSet) {
                    for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                        if (f.getType() == boolean.class && f.getName().equals("renderBackground")) {
                            f.setAccessible(true);
                            f.setBoolean(this, false);
                            fieldSet = true;
                            break;
                        }
                    }
                    c = c.getSuperclass();
                }
                com.trailblazer.fabric.TrailblazerFabricClient.LOGGER.debug("PathListWidget: background disabled via {}",
                        fieldSet ? "field" : (disabledViaMethod ? "method" : "none"));
            } catch (Throwable t) {
                com.trailblazer.fabric.TrailblazerFabricClient.LOGGER.debug("PathListWidget: could not disable built-in background.");
            }
        } else {
            com.trailblazer.fabric.TrailblazerFabricClient.LOGGER.debug("PathListWidget: background disabled via method.");
        }
    }

    // Note: The built-in background is suppressed via a mixin into EntryListWidget#drawMenuListBackground.

    @Override
    protected void renderList(DrawContext context, int mouseX, int mouseY, float delta) {
        // Container-wide background, final color: semi-transparent black with ~30% reduced opacity
        int left = 0;
        int right = this.width;
        int top = this.getY();
        int bottom = this.getY() + this.getHeight();
        context.fill(left, top, right, bottom, 0x0B000000);
        super.renderList(context, mouseX, mouseY, delta);
    }

    @Override
    protected void renderDecorations(DrawContext context, int mouseX, int mouseY) {
        // Restore default decorations (scrollbar, selection highlight)
        super.renderDecorations(context, mouseX, mouseY);
    }

    @Override
    protected void drawHeaderAndFooterSeparators(DrawContext context) {
        super.drawHeaderAndFooterSeparators(context);
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

    public class PathEntry extends ElementListWidget.Entry<PathEntry> {
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
            this.shareButton.active = serverAvailable && origin != PathOrigin.SERVER_SHARED;

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
            // Unified background block for the row
            int bgLeft = x + 4;
            int bgRight = x + entryWidth - 4;
            int rowTop = y;
            int rowContentBottom = y + entryHeight;
            int fullRowBottom = rowTop + PathListWidget.this.itemHeight;
            int rowBottom = Math.max(rowContentBottom, fullRowBottom);

            // Row background: neutral semi-transparent dark to keep focus on content
            int bgColor = 0x33000000;
            context.fill(bgLeft, rowTop, bgRight, rowBottom, bgColor);
            // Draw top border per row to keep crisp separators, and bottom border for the final row
            context.fill(bgLeft, rowTop, bgRight, rowTop + 1, 0xFF000000);
            if (index == PathListWidget.this.getEntryCount() - 1) {
                context.fill(bgLeft, rowBottom - 1, bgRight, rowBottom, 0xFF000000);
            }
            // Draw left and right borders
            context.fill(bgLeft, rowTop, bgLeft + 1, rowBottom, 0xFF000000);
            context.fill(bgRight - 1, rowTop, bgRight, rowBottom, 0xFF000000);

            // Layout metrics
            final int topPadding = 4; // desired visual gap
            final int buttonYOffset = topPadding; // button box y
            final int textBaselineY = topPadding + y + 5; // tune baseline to align with button label vertically
            int baseX = bgLeft + 4; // horizontal padding inside background

            // Origin badge & path name
            if (origin != null) {
                int badgeColor = originBadgeColor();
                context.fill(baseX, textBaselineY - 1, baseX + 8, textBaselineY - 1 + 8, badgeColor);
                // Tooltip hit box
                if (mouseX >= baseX && mouseX <= baseX + 8 && mouseY >= textBaselineY - 1 && mouseY <= textBaselineY - 1 + 8) {
                    context.drawTooltip(MinecraftClient.getInstance().textRenderer, getOriginTooltipText(), mouseX, mouseY);
                }
                baseX += 12;
            }

            int buttonWidth = 60;
            int buttonHeight = 18;
            int buttonSpacing = buttonWidth + 8;
            int buttonAreaWidth = isMyPath ? (buttonSpacing * 4) : (buttonSpacing * 2);
            int availableTextWidth = (bgRight - bgLeft) - (baseX - bgLeft) - buttonAreaWidth - 8;
            var textRenderer = MinecraftClient.getInstance().textRenderer;
            String displayName = path.getPathName();
            if (textRenderer.getWidth(displayName) > availableTextWidth) {
                displayName = textRenderer.trimToWidth(displayName, availableTextWidth - textRenderer.getWidth("...")) + "...";
            }
            context.drawText(textRenderer, displayName, baseX, textBaselineY, 0xFFFFFFFF, true);
            if (!isMyPath) {
                String ownerText = " (by " + path.getOwnerName() + ")";
                int ownerX = baseX + textRenderer.getWidth(displayName);
                int remainingWidth = availableTextWidth - textRenderer.getWidth(displayName);
                if (textRenderer.getWidth(ownerText) > remainingWidth) {
                    ownerText = textRenderer.trimToWidth(ownerText, remainingWidth - textRenderer.getWidth("...")) + "...";
                }
                context.drawTextWithShadow(textRenderer, ownerText, ownerX, textBaselineY, 0xFF999999);
            }

            // Buttons aligned to right inside background band
            int buttonX = bgRight - (buttonWidth + 2);
            int buttonY = y + buttonYOffset;
            toggleButton.setX(buttonX); toggleButton.setY(buttonY); toggleButton.setWidth(buttonWidth); toggleButton.setHeight(buttonHeight);
            toggleButton.render(context, mouseX, mouseY, tickDelta);
            if (isMyPath) {
                buttonX -= buttonSpacing; shareButton.setX(buttonX); shareButton.setY(buttonY); shareButton.setWidth(buttonWidth); shareButton.setHeight(buttonHeight); shareButton.render(context, mouseX, mouseY, tickDelta);
                buttonX -= buttonSpacing; editButton.setX(buttonX); editButton.setY(buttonY); editButton.setWidth(buttonWidth); editButton.setHeight(buttonHeight); editButton.render(context, mouseX, mouseY, tickDelta);
            }
            buttonX -= buttonSpacing; deleteButton.setX(buttonX); deleteButton.setY(buttonY); deleteButton.setWidth(buttonWidth); deleteButton.setHeight(buttonHeight); deleteButton.render(context, mouseX, mouseY, tickDelta);

            // Coordinates positioned relative to content to avoid button overlap while respecting bottom padding
            int coordMaxY = rowBottom - textRenderer.fontHeight - 1;
            // Original baseline (pre-dynamic changes) — keep behavior and move it exactly 1px down.
            int originalBaseline = rowBottom - 1 - 3 - textRenderer.fontHeight; // 3px bottom padding before baseline
            int coordBaseline = Math.min(originalBaseline + 2, coordMaxY); // Push down by 1 more pixel
            if (coordBaseline >= rowTop + topPadding) {
                drawCoordinates(context, baseX, bgRight - 10, coordBaseline, textRenderer);
            }

            if (awaitingDeleteConfirm && System.currentTimeMillis() - deleteConfirmStartMs > CONFIRM_TIMEOUT_MS) {
                awaitingDeleteConfirm = false;
                deleteButton.setMessage(origin == PathOrigin.SERVER_SHARED ? Text.of("Remove") : Text.of("Delete"));
            }
        }

        private Text getToggleButtonText() {
            boolean isVisible = pathManager.isPathVisible(path.getPathId());
            return Text.of("Toggle: " + (isVisible ? "ON" : "OFF"))
                .copy()
                .formatted(isVisible ? Formatting.DARK_GREEN : Formatting.DARK_RED);
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
                case LOCAL -> 0xFF4CAF50; // More subtle green
                case SERVER_OWNED, SERVER_SHARED -> 0xFF2196F3; // More subtle blue
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

            context.drawTextWithShadow(tr, startText, startX, yCoord, 0xFF808080); // More muted gray
            context.drawTextWithShadow(tr, arrow, arrowX, yCoord, 0xFF606060); // Subtle arrow
            context.drawTextWithShadow(tr, endText, endX, yCoord, 0xFF808080); // Consistent muted gray
        }
    }
}
