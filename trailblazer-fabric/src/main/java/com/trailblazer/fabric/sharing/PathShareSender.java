package com.trailblazer.fabric.sharing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.google.gson.Gson;
import com.trailblazer.api.PathData;
import com.trailblazer.fabric.networking.payload.c2s.SharePathRequestPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Utility responsible for packaging a path into a share payload and sending it to the relay server.
 */
public final class PathShareSender {

    private static final Gson GSON = new Gson();

    private PathShareSender() {
    }

    public static void sharePath(PathData path, Collection<UUID> recipients) {
        if (path == null || recipients == null || recipients.isEmpty()) {
            return;
        }
        List<UUID> resolvedRecipients = (recipients instanceof List<UUID> list)
                ? list
                : new ArrayList<>(recipients);
        String json = GSON.toJson(path);
        ClientPlayNetworking.send(new SharePathRequestPayload(resolvedRecipients, json));
    }
}
