package com.trailblazer.fabric.ui;

import com.trailblazer.fabric.ClientPathManager;
import com.trailblazer.api.PathData;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class MainMenuScreen extends Screen {
    private final ClientPathManager pathManager;
    private ButtonWidget myPathsTab;
    private ButtonWidget sharedWithMeTab;
    private PathListWidget pathListWidget;

    private boolean showingMyPaths = true;

    public MainMenuScreen(ClientPathManager pathManager) {
        super(Text.of("Trailblazer Main Menu"));
        this.pathManager = pathManager;
    }

    @Override
    protected void init() {
        super.init();

        int tabWidth = 100;
        int tabHeight = 20;
        int tabY = 30;

        myPathsTab = ButtonWidget.builder(Text.of("My Paths"), button -> {
            showingMyPaths = true;
            updatePathList();
        }).dimensions(this.width / 2 - tabWidth - 5, tabY, tabWidth, tabHeight).build();

        sharedWithMeTab = ButtonWidget.builder(Text.of("Shared With Me"), button -> {
            showingMyPaths = false;
            updatePathList();
        }).dimensions(this.width / 2 + 5, tabY, tabWidth, tabHeight).build();

        this.addDrawableChild(myPathsTab);
        this.addDrawableChild(sharedWithMeTab);

        pathListWidget = new PathListWidget(this.client, this.width, this.height - 80, 60, 20);
        this.addDrawableChild(pathListWidget);

        updatePathList();
    }

    private void updatePathList() {
        pathListWidget.clearEntries();
        List<PathData> paths = new ArrayList<>(showingMyPaths ? pathManager.getMyPaths() : pathManager.getSharedPaths());
        for (PathData path : paths) {
            pathListWidget.addEntry(new PathListWidget.PathEntry(path, pathManager, showingMyPaths));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);
    }
}

