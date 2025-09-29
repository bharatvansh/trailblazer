package com.trailblazer.fabric.ui;

import com.trailblazer.api.PathData;
import com.trailblazer.fabric.ClientPathManager;
import com.trailblazer.fabric.ClientPathManager.PathOrigin;
import com.trailblazer.fabric.networking.payload.c2s.UpdatePathMetadataPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.client.gui.DrawContext;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.List;

public class PathCreationScreen extends Screen {
    private final ClientPathManager pathManager;
    private final Consumer<PathData> onSave;
    private final PathData editingPath;
    private TextFieldWidget nameField;
    private ButtonWidget saveButton;
    private ButtonWidget cancelButton;
    private ButtonWidget cycleColorButton;
    private TextFieldWidget hexColorField;
    private int workingColor = 0; // 0 means unset -> will default

    public PathCreationScreen(ClientPathManager pathManager, Consumer<PathData> onSave) {
        this(pathManager, onSave, null);
    }

    public PathCreationScreen(ClientPathManager pathManager, Consumer<PathData> onSave, PathData editingPath) {
        super(editingPath == null ? Text.of("Create New Path") : Text.of("Edit Path"));
        this.pathManager = pathManager;
        this.onSave = onSave;
        this.editingPath = editingPath;
    }

    @Override
    protected void init() {
        super.init();
        int fieldWidth = 200;
        int fieldHeight = 20;
        int fieldX = this.width / 2 - fieldWidth / 2;
        int fieldY = this.height / 2 - fieldHeight / 2;

        nameField = new TextFieldWidget(this.textRenderer, fieldX, fieldY, fieldWidth, fieldHeight, Text.of("Path Name"));
        if (editingPath != null) {
            nameField.setText(editingPath.getPathName());
            workingColor = editingPath.getColorArgb();
        }
        this.addDrawableChild(nameField);

        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonY = fieldY + fieldHeight + 10;

        // Color cycle button
        cycleColorButton = ButtonWidget.builder(Text.of(colorButtonLabel()), button -> {
            java.util.List<Integer> palette = com.trailblazer.api.PathColors.palette();
            if (workingColor == 0) {
                workingColor = palette.get(0);
            } else {
                int idx = 0;
                for (int i = 0; i < palette.size(); i++) {
                    if (palette.get(i) == workingColor) { idx = i; break; }
                }
                workingColor = palette.get((idx + 1) % palette.size());
            }
            cycleColorButton.setMessage(Text.of(colorButtonLabel()));
            if (hexColorField != null) {
                hexColorField.setText(String.format("#%06X", workingColor & 0xFFFFFF));
            }
        }).dimensions(this.width / 2 - buttonWidth - 5, buttonY, buttonWidth, buttonHeight).build();
        this.addDrawableChild(cycleColorButton);

        hexColorField = new TextFieldWidget(this.textRenderer, this.width / 2 + 5, buttonY, buttonWidth, buttonHeight, Text.of("#RRGGBB"));
        if (workingColor != 0) {
            hexColorField.setText(String.format("#%06X", workingColor & 0xFFFFFF));
        }
        this.addDrawableChild(hexColorField);

        buttonY += 30;

        saveButton = ButtonWidget.builder(Text.of("Save"), button -> {
            String name = nameField.getText();
            if (name.isEmpty()) {
                name = "New Path " + System.currentTimeMillis();
            }

            // Parse hex field if user modified
            String hex = hexColorField.getText();
            java.util.Optional<Integer> parsed = com.trailblazer.api.PathColors.parse(hex);
            if (parsed.isPresent()) {
                workingColor = parsed.get();
            }

            if (editingPath != null) {
                editingPath.setPathName(name);
                if (workingColor != 0) {
                    editingPath.setColorArgb(workingColor);
                }
                onSave.accept(editingPath);
                // If this path is server-owned, propagate metadata changes to server.
                PathOrigin origin = pathManager.getPathOrigin(editingPath.getPathId());
                if (origin == PathOrigin.SERVER_OWNED && ClientPlayNetworking.canSend(UpdatePathMetadataPayload.ID)) {
                    int colorToSend = editingPath.getColorArgb();
                    ClientPlayNetworking.send(new UpdatePathMetadataPayload(editingPath.getPathId(), editingPath.getPathName(), colorToSend));
                }
            } else {
                UUID ownerUuid = pathManager.getLocalPlayerUuid() != null ? pathManager.getLocalPlayerUuid() : UUID.randomUUID();
                String ownerName = MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getGameProfile().getName() : "Player";
                String dimension = MinecraftClient.getInstance().player != null ?
                        MinecraftClient.getInstance().player.getWorld().getRegistryKey().getValue().toString() : "minecraft:overworld";
                PathData newPath = new PathData(UUID.randomUUID(), name, ownerUuid, ownerName, System.currentTimeMillis(), dimension, List.of());
                if (workingColor != 0) {
                    newPath.setColorArgb(workingColor);
                }
                onSave.accept(newPath);
            }
            this.client.setScreen(null);
        }).dimensions(this.width / 2 - buttonWidth - 5, buttonY, buttonWidth, buttonHeight).build();

        cancelButton = ButtonWidget.builder(Text.of("Cancel"), button -> {
            this.client.setScreen(null);
        }).dimensions(this.width / 2 + 5, buttonY, buttonWidth, buttonHeight).build();

        this.addDrawableChild(saveButton);
        this.addDrawableChild(cancelButton);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
    }

    private String colorButtonLabel() {
        int color = workingColor;
        if (color == 0 && editingPath != null) {
            color = editingPath.getColorArgb();
        }
        if (color == 0) {
            return "Color: (auto)";
        }
        return "Color: " + com.trailblazer.api.PathColors.nameOrHex(color);
    }
}
