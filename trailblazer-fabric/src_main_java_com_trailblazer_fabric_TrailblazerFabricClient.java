import com.trailblazer.fabric.config.TrailblazerClientConfig;
import com.trailblazer.fabric.ui.RecordingOverlay;
import com.trailblazer.fabric.commands.TrailblazerCommand;
import com.trailblazer.fabric.rendering.PathRenderer;

import net.fabricmc.api.ClientModInitializer;

public class TrailblazerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TrailblazerClientConfig config = TrailblazerClientConfig.load();
        ClientPathManager clientPathManager = new ClientPathManager(config);

        if (config.recordingOverlayEnabled) {
            net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(new RecordingOverlay(clientPathManager));
        }
        TrailblazerCommand.register(clientPathManager);
        registerWorldLifecycle();
        registerClientTick();
    }

    private void registerWorldLifecycle() {
        // Implementation for registering world lifecycle events
    }

    private void registerClientTick() {
        // Implementation for registering client tick events
    }
}