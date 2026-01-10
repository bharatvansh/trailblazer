package com.trailblazer.fabric.networking.payload.c2s;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.trailblazer.fabric.TrailblazerFabricClient;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Sends a locally recorded path to the server so it can forward the payload to specific recipients.
 */
public record SharePathRequestPayload(List<UUID> recipients, String pathJson) implements CustomPayload {

    public static final Id<SharePathRequestPayload> ID =
            new Id<>(Identifier.of(TrailblazerFabricClient.MOD_ID, "share_request"));

    public static final PacketCodec<RegistryByteBuf, SharePathRequestPayload> CODEC = PacketCodec.of(
        SharePathRequestPayload::write,
        SharePathRequestPayload::read
    );

    /** Defensive cap to prevent oversized payloads from crashing the client or server. */
    private static final int MAX_JSON_BYTES = 1_048_576;

    private static void write(SharePathRequestPayload payload, RegistryByteBuf buf) {
        List<UUID> ids = payload.recipients();
        buf.writeVarInt(ids.size());
        for (UUID id : ids) {
            buf.writeUuid(id);
        }

        // IMPORTANT: do NOT use buf.writeString() here.
        // Minecraft's string serialization has a ~32k cap, which is far too small for PathData JSON.
        // The Paper plugin expects a VarInt length followed by raw UTF-8 bytes.
        writeUtfBytes(buf, payload.pathJson());
    }

    private static SharePathRequestPayload read(RegistryByteBuf buf) {
        int count = buf.readVarInt();
        List<UUID> recipients = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            recipients.add(buf.readUuid());
        }
        String pathJson = readUtfBytes(buf);
        return new SharePathRequestPayload(recipients, pathJson);
    }

    private static void writeUtfBytes(RegistryByteBuf buf, String s) {
        String safe = (s != null) ? s : "";
        byte[] bytes = safe.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_JSON_BYTES) {
            throw new IllegalArgumentException("Path JSON too large to send: " + bytes.length + " bytes");
        }
        buf.writeVarInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readUtfBytes(RegistryByteBuf buf) {
        int len = buf.readVarInt();
        if (len < 0 || len > MAX_JSON_BYTES || len > buf.readableBytes()) {
            throw new IllegalStateException("Invalid path JSON length: " + len);
        }
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
