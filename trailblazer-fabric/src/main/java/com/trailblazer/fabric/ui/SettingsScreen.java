package com.trailblazer.fabric.ui;

import com.trailblazer.fabric.RenderSettingsManager;
import com.trailblazer.fabric.rendering.RenderMode;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class SettingsScreen extends Screen {
    private final RenderSettingsManager renderSettingsManager;
    private final Screen parent;

    public SettingsScreen(RenderSettingsManager renderSettingsManager, Screen parent) {
        super(Text.of("Trailblazer Settings"));
        this.renderSettingsManager = renderSettingsManager;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int buttonWidth = 200;
        int buttonHeight = 20;
        int buttonX = this.width / 2 - buttonWidth / 2;
        int buttonY = this.height / 2 - 40;

        ButtonWidget renderModeButton = ButtonWidget.builder(Text.of("Render Mode: " + renderSettingsManager.getRenderMode()), button -> {
            RenderMode currentMode = renderSettingsManager.getRenderMode();
            RenderMode nextMode = currentMode.next();
            renderSettingsManager.setRenderMode(nextMode);
            button.setMessage(Text.of("Render Mode: " + nextMode));
        }).dimensions(buttonX, buttonY, buttonWidth, buttonHeight).build();
        this.addDrawableChild(renderModeButton);

        this.addDrawableChild(ButtonWidget.builder(Text.of("Configure Keybinding"), button -> {
            // Logic to open the keybinding configuration screen
        }).dimensions(buttonX, buttonY + 30, buttonWidth, buttonHeight).build());

        this.addDrawableChild(ButtonWidget.builder(Text.of("Back"), button -> {
            this.client.setScreen(parent);
        }).dimensions(this.width / 2 - 100, this.height - 40, 200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
    }
}
