package com.trailblazer.fabric.ui;

import com.trailblazer.api.PathData;
import com.trailblazer.fabric.ClientPathManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
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
        }
        this.addDrawableChild(nameField);

        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonY = fieldY + fieldHeight + 10;

        saveButton = ButtonWidget.builder(Text.of("Save"), button -> {
            String name = nameField.getText();
            if (name.isEmpty()) {
                name = "New Path " + System.currentTimeMillis();
            }

            if (editingPath != null) {
                editingPath.setPathName(name);
                onSave.accept(editingPath);
            } else {
                // This is a placeholder. The actual path creation will be handled by the recorder.
                // Here we just create a dummy path to demonstrate the UI flow.
                PathData newPath = new PathData(UUID.randomUUID(), name, UUID.randomUUID(), "Player", System.currentTimeMillis(), "overworld", List.of());
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
}
