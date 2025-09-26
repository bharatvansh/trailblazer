package com.trailblazer.fabric.networking;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.trailblazer.api.PathData;
import com.trailblazer.api.Vector3d;
import com.trailblazer.fabric.ClientPathManager;
import com.trailblazer.fabric.TrailblazerFabricClient;
import com.trailblazer.fabric.networking.payload.s2c.HideAllPathsPayload;
import com.trailblazer.fabric.networking.payload.s2c.LivePathUpdatePayload;
import com.trailblazer.fabric.networking.payload.s2c.PathDataSyncPayload;
import com.trailblazer.fabric.networking.payload.s2c.SharedPathPayload;
import com.trailblazer.fabric.networking.payload.s2c.StopLivePathPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Handles the logic for receiving packets on the client.
 */
public class ClientPacketHandler {

    private static final Gson GSON = new Gson();

    public static void registerS2CPackets(ClientPathManager pathManager) {
        // THE FIX: Explicitly define the types for all lambda parameters.
        ClientPlayNetworking.registerGlobalReceiver(PathDataSyncPayload.ID, (payload, context) -> {
            String json = payload.json();

            final List<PathData> receivedPaths;
            try {
                Type listType = new TypeToken<List<PathData>>() {}.getType();
                List<PathData> parsed = GSON.fromJson(json, listType);
                if (parsed == null) {
                    parsed = Collections.emptyList();
                }
                receivedPaths = parsed;
            } catch (Exception e) {
                TrailblazerFabricClient.LOGGER.error("Failed to parse PathData JSON from server:", e);
                return;
            }

            context.client().execute(() -> pathManager.applyServerSync(receivedPaths));
        });

        // Register a listener for our new "hide all" signal.
        ClientPlayNetworking.registerGlobalReceiver(HideAllPathsPayload.ID, (payload, context) -> {
            // This is a simple signal, so we just need to execute the action.
            context.client().execute(pathManager::hideAllPaths);
        });

        ClientPlayNetworking.registerGlobalReceiver(LivePathUpdatePayload.ID, (payload, context) -> {
            String json = payload.json();
            final List<Vector3d> points;
            try {
                Type listType = new TypeToken<List<Vector3d>>() {}.getType();
                points = GSON.fromJson(json, listType);
            } catch (Exception e) {
                TrailblazerFabricClient.LOGGER.error("Failed to parse live path points from server:", e);
                return;
            }

            if (points != null) {
                context.client().execute(() -> pathManager.updateLivePath(points));
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(StopLivePathPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                pathManager.stopLivePath();
                // After recording stops, request a fresh sync so newly saved path appears immediately
                try {
                    if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(
                            com.trailblazer.fabric.networking.payload.c2s.HandshakePayload.ID)) {
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                new com.trailblazer.fabric.networking.payload.c2s.HandshakePayload());
                    }
                } catch (Exception e) {
                    TrailblazerFabricClient.LOGGER.error("Failed to request path resync after recording stop", e);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SharedPathPayload.ID, (payload, context) ->
            context.client().execute(() -> pathManager.applyServerShare(payload.path()))
        );
    }
}