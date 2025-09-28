import com.trailblazer.fabric.config.TrailblazerClientConfig;
import com.trailblazer.fabric.ui.RecordingOverlay;
import com.trailblazer.fabric.commands.TrailblazerCommand;
import com.trailblazer.fabric.rendering.PathRenderer;

import net.fabricmc.api.ClientModInitializer;

public class TrailblazerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {