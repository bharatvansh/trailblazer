package com.trailblazer.fabric;

import com.trailblazer.fabric.networking.ServerIntegrationManager;

/**
 * Simple bridge to expose the current {@link ServerIntegrationManager} to UI components
 * without creating tight coupling back to the client initializer.
 */
public final class ServerIntegrationBridge {
    private ServerIntegrationBridge() {}

    public static ServerIntegrationManager SERVER_INTEGRATION;
}
