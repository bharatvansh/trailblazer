package com.trailblazer.fabric.ui;

import com.trailblazer.fabric.RenderSettingsManager;
import com.trailblazer.fabric.rendering.RenderMode;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class SettingsScreen extends Screen {
    private final RenderSettingsManager renderSettingsManager;

    public SettingsScreen(RenderSettingsManager renderSettingsManager) {
        super(Text.of("Trailblazer Settings"));
        this.renderSettingsManager = renderSettingsManager;
    }

    @Override
    protected void init() {
        super.init();

        int buttonWidth = 200;
        int buttonHeight = 20;
        int buttonX = this.width / 2 - buttonWidth / 2;
        int buttonY = this.height / 2 - 40;

        this.addDrawableChild(ButtonWidget.builder(Text.of("Render Mode: " + renderSettingsManager.getRenderMode()), button -> {
            RenderMode currentMode = renderSettingsManager.getRenderMode();
            RenderMode nextMode = currentMode.next();
            renderSettingsManager.setRenderMode(nextMode);
            button.setMessage(Text.of("Render Mode: " + nextMode));
        }).dimensions(buttonX, buttonY, buttonWidth, buttonHeight).build());

        this.addDrawableChild(ButtonWidget.builder(Text.of("Configure Keybinding"), button -> {
            // Logic to open the keybinding configuration screen
        }).dimensions(buttonX, buttonY + 30, buttonWidth, buttonHeight).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
    }
}
