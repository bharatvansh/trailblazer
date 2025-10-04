package com.trailblazer.fabric.ui;

import com.trailblazer.api.PathData;
import com.trailblazer.fabric.ClientPathManager;
import com.trailblazer.fabric.ClientPathManager.PathOrigin;
import com.trailblazer.fabric.networking.payload.c2s.SharePathRequestPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import java.util.List;

public class PathListWidget extends ElementListWidget<PathListWidget.PathEntry> {
    private double targetScrollAmount = -1.0;
    private double scrollVelocity = 0.0;
    private static final double FRICTION_PER_SEC = 1.2;
    private static final double MIN_VELOCITY = 0.05;
    private static final double MAX_VELOCITY = 500.0;

    private static final int CONTAINER_BG      = 0x2E000000;
    private static final int ROW_BG            = 0x50000000;
    private static final int ROW_BG_HOVER      = 0x5A000000;
    private static final int SEPARATOR         = 0x40FFFFFF;

    public PathListWidget(MinecraftClient client, int width, int height, int top, int itemHeight) {
        super(client, width, height, top, Math.max(itemHeight, 36));
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

    @Override
    protected void drawMenuListBackground(DrawContext context) {
        com.trailblazer.fabric.TrailblazerFabricClient.LOGGER.debug("PathListWidget: drawMenuListBackground invoked (suppressed)");
    }

    protected void drawInWorldMenuListBackground(DrawContext context) {
        com.trailblazer.fabric.TrailblazerFabricClient.LOGGER.debug("PathListWidget: drawInWorldMenuListBackground invoked (suppressed)");
    }

    @Override
    protected void renderList(DrawContext context, int mouseX, int mouseY, float delta) {
        int left = 0;
        int right = this.width;
        int top = this.getY();
        int bottom = this.getY() + this.getHeight();
        context.fill(left, top, right, bottom, CONTAINER_BG);
        context.fill(left, top, right, top + 1, SEPARATOR);
        context.fill(left, bottom - 1, right, bottom, SEPARATOR);

        if (targetScrollAmount < 0.0) {
            targetScrollAmount = this.getScrollAmount();
        }

        double dt = Math.max(0.0, Math.min(delta, 1.0));
        if (scrollVelocity != 0.0) {
            targetScrollAmount += scrollVelocity * dt;
            double decay = Math.exp(-FRICTION_PER_SEC * dt);
            scrollVelocity *= decay;
            if (Math.abs(scrollVelocity) < 80.0) scrollVelocity *= 0.80;
            if (Math.abs(scrollVelocity) < MIN_VELOCITY) scrollVelocity = 0.0;
        }

        double max = this.getMaxScroll();
        if (targetScrollAmount <= 0.0) {
            targetScrollAmount = 0.0;
            if (scrollVelocity < 0) scrollVelocity *= -0.2;
        } else if (targetScrollAmount >= max) {
            targetScrollAmount = max;
            if (scrollVelocity > 0) scrollVelocity *= -0.2;
        }
        this.setScrollAmount(targetScrollAmount);
        int contentTop = top + 1;
        int contentBottom = bottom - 1;
        context.enableScissor(left, contentTop, right, contentBottom);
        super.renderList(context, mouseX, mouseY, delta);
        context.disableScissor();
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!this.isMouseOver(mouseX, mouseY)) return false;
        if (targetScrollAmount < 0.0) targetScrollAmount = this.getScrollAmount();
        double base = Math.max(this.itemHeight * 0.35, 8.0);
        double deltaV = -amount * base * 1.2;
        if (deltaV > 180.0) deltaV = 180.0;
        if (deltaV < -180.0) deltaV = -180.0;
        scrollVelocity += deltaV;
        if (scrollVelocity > MAX_VELOCITY) scrollVelocity = MAX_VELOCITY;
        if (scrollVelocity < -MAX_VELOCITY) scrollVelocity = -MAX_VELOCITY;
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return mouseScrolled(mouseX, mouseY, verticalAmount);
    }

    @Override
    protected int getRowTop(int index) {
        return super.getRowTop(index) - 3;
    }

    @Override
    protected int getRowBottom(int index) {
        return super.getRowBottom(index) - 3;
    }

    @Override
    protected void renderDecorations(DrawContext context, int mouseX, int mouseY) {
        super.renderDecorations(context, mouseX, mouseY);
    }

    @Override
    protected void drawHeaderAndFooterSeparators(DrawContext context) {
    }

    @Override
    protected void drawSelectionHighlight(DrawContext context, int a, int b, int c, int d, int e) {
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
    private final net.minecraft.text.Text shareDisabledTooltip;
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
            boolean canSend = ClientPlayNetworking.canSend(SharePathRequestPayload.ID);
            boolean originAllows = (origin == PathOrigin.LOCAL || origin == PathOrigin.IMPORTED || origin == PathOrigin.SERVER_OWNED);
            boolean canShare = canSend && originAllows;
            this.shareButton.active = canShare;
            if (!canSend) {
                this.shareDisabledTooltip = Text.of("Server-side plugin required");
            } else {
                this.shareDisabledTooltip = null;
            }

            this.editButton = ButtonWidget.builder(Text.of("Edit"), button -> {
                MinecraftClient.getInstance().setScreen(new PathCreationScreen(pathManager, updatedPath -> {
                    pathManager.onPathUpdated(updatedPath);
                }, path, MinecraftClient.getInstance().currentScreen));
            }).build();
            if (!(origin == PathOrigin.LOCAL || origin == PathOrigin.IMPORTED || origin == PathOrigin.SERVER_OWNED)) {
                this.editButton.active = false;
                this.editButton.setMessage(Text.of("View"));
            }

            this.deleteButton = ButtonWidget.builder(Text.of("Delete"), button -> {
                long now = System.currentTimeMillis();
                if (!awaitingDeleteConfirm || now - deleteConfirmStartMs > CONFIRM_TIMEOUT_MS) {
                    awaitingDeleteConfirm = true;
                    deleteConfirmStartMs = now;
                    button.setMessage(Text.of("Confirm"));
                    return;
                }
                awaitingDeleteConfirm = false;
                switch (origin) {
                    case LOCAL, IMPORTED -> {
                        pathManager.deletePath(path.getPathId());
                        button.setMessage(Text.of("Delete"));
                    }
                    case SERVER_OWNED -> {
                        if (ClientPlayNetworking.canSend(com.trailblazer.fabric.networking.payload.c2s.DeletePathPayload.ID)) {
                            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                    new com.trailblazer.fabric.networking.payload.c2s.DeletePathPayload(path.getPathId()));
                        }
                        pathManager.removeServerPath(path.getPathId());
                        button.setMessage(Text.of("Delete"));
                    }
                    case SERVER_SHARED -> {
                        pathManager.removeSharedPath(path.getPathId());
                        button.setMessage(Text.of("Remove"));
                    }
                }
            }).build();
            if (origin == PathOrigin.SERVER_SHARED) {
                this.deleteButton.setMessage(Text.of("Remove"));
            }
            if (origin == PathOrigin.SERVER_SHARED) {
                this.shareButton.active = false;
            }
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int bgLeft = x + 4;
            int bgRight = x + entryWidth - 4;
            int rowTop = y;
            int rowContentBottom = y + entryHeight;
            int fullRowBottom = rowTop + PathListWidget.this.itemHeight;
            int rowBottom = Math.max(rowContentBottom, fullRowBottom);

            int bgColor = hovered ? ROW_BG_HOVER : ROW_BG;
            context.fill(bgLeft, rowTop, bgRight, rowBottom, bgColor);

            context.fill(bgLeft, rowTop, bgRight, rowTop + 1, SEPARATOR);
            if (index == PathListWidget.this.getEntryCount() - 1) {
                context.fill(bgLeft, rowBottom - 1, bgRight, rowBottom, SEPARATOR);
            }

            final int topPadding = 4;
            final int buttonYOffset = topPadding;
            final int textBaselineY = topPadding + y + 5;
            int baseX = bgLeft + 4;

            if (origin != null) {
                int badgeColor = originBadgeColor();
                context.fill(baseX, textBaselineY - 1, baseX + 8, textBaselineY - 1 + 8, badgeColor);
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
            if (!isMyPath || origin == PathOrigin.IMPORTED) {
                String ownerName = origin == PathOrigin.IMPORTED ? path.getOriginOwnerName() : path.getOwnerName();
                if (ownerName != null && !ownerName.isBlank()) {
                    String ownerText = " (by " + ownerName + ")";
                    int ownerX = baseX + textRenderer.getWidth(displayName);
                    int remainingWidth = availableTextWidth - textRenderer.getWidth(displayName);
                    if (textRenderer.getWidth(ownerText) > remainingWidth) {
                        ownerText = textRenderer.trimToWidth(ownerText, remainingWidth - textRenderer.getWidth("...")) + "...";
                    }
                    context.drawTextWithShadow(textRenderer, ownerText, ownerX, textBaselineY, 0xFF999999);
                }
            }

            int buttonX = bgRight - (buttonWidth + 2);
            int buttonY = y + buttonYOffset;
            toggleButton.setX(buttonX); toggleButton.setY(buttonY); toggleButton.setWidth(buttonWidth); toggleButton.setHeight(buttonHeight);
            toggleButton.render(context, mouseX, mouseY, tickDelta);
            if (isMyPath) {
                buttonX -= buttonSpacing; shareButton.setX(buttonX); shareButton.setY(buttonY); shareButton.setWidth(buttonWidth); shareButton.setHeight(buttonHeight); shareButton.render(context, mouseX, mouseY, tickDelta);
                buttonX -= buttonSpacing; editButton.setX(buttonX); editButton.setY(buttonY); editButton.setWidth(buttonWidth); editButton.setHeight(buttonHeight); editButton.render(context, mouseX, mouseY, tickDelta);
            }
            buttonX -= buttonSpacing; deleteButton.setX(buttonX); deleteButton.setY(buttonY); deleteButton.setWidth(buttonWidth); deleteButton.setHeight(buttonHeight); deleteButton.render(context, mouseX, mouseY, tickDelta);

            int coordMaxY = rowBottom - textRenderer.fontHeight - 1;
            int originalBaseline = rowBottom - 1 - 3 - textRenderer.fontHeight;
            int coordBaseline = Math.min(originalBaseline + 2, coordMaxY);
            if (coordBaseline >= rowTop + topPadding) {
                drawCoordinates(context, baseX, bgRight - 10, coordBaseline, textRenderer);
            }

            if (awaitingDeleteConfirm && System.currentTimeMillis() - deleteConfirmStartMs > CONFIRM_TIMEOUT_MS) {
                awaitingDeleteConfirm = false;
                deleteButton.setMessage(origin == PathOrigin.SERVER_SHARED ? Text.of("Remove") : Text.of("Delete"));
            }

            // Show tooltip for disabled share button when server-side plugin is required
            if (isMyPath && shareDisabledTooltip != null) {
                int sx = shareButton.getX();
                int sy = shareButton.getY();
                int sw = shareButton.getWidth();
                int sh = shareButton.getHeight();
                if (mouseX >= sx && mouseX <= sx + sw && mouseY >= sy && mouseY <= sy + sh) {
                    context.drawTooltip(MinecraftClient.getInstance().textRenderer, shareDisabledTooltip, mouseX, mouseY);
                }
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
                case LOCAL -> Text.of("Stored on this client");
                case IMPORTED -> Text.of("Imported share stored locally");
                case SERVER_OWNED -> Text.of("Provided by server (your copy)");
                case SERVER_SHARED -> Text.of("Live server share (read-only)");
            };
        }

        private int originBadgeColor() {
            return switch (origin) {
                case LOCAL -> 0xFF4CAF50;
                case IMPORTED -> 0xFF9C27B0;
                case SERVER_OWNED -> 0xFF2196F3;
                case SERVER_SHARED -> 0xFF03A9F4;
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
            int availableForStart = maxWidth - endWidth - arrowWidth - 16;
            if (availableForStart < 40) return;

            if (tr.getWidth(startText) > availableForStart) {
                startText = tr.trimToWidth(startText, Math.max(availableForStart - tr.getWidth("..."), 0)) + "...";
            }

            int startWidth = tr.getWidth(startText);
            int startX = baseX;
            int endX = contentRightX - endWidth;

            int arrowX = baseX + (maxWidth - arrowWidth) / 2;
            int minArrowX = startX + startWidth + 4;
            int maxArrowX = endX - arrowWidth - 4;
            if (arrowX < minArrowX) {
                arrowX = minArrowX;
            } else if (arrowX > maxArrowX) {
                arrowX = maxArrowX;
            }

            context.drawTextWithShadow(tr, startText, startX, yCoord, 0xFF808080);
            context.drawTextWithShadow(tr, arrow, arrowX, yCoord, 0xFF606060);
            context.drawTextWithShadow(tr, endText, endX, yCoord, 0xFF808080);
        }
    }
}
