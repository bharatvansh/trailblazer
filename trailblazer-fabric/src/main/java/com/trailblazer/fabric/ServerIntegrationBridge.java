package com.trailblazer.fabric;

import com.trailblazer.fabric.networking.ServerIntegrationManager;

/**
 * Exposes server integration to UI components.
 */
public final class ServerIntegrationBridge {
    private ServerIntegrationBridge() {}

    public static ServerIntegrationManager SERVER_INTEGRATION;
}
