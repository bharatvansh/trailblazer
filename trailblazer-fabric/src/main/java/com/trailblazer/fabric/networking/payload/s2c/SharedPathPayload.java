package com.trailblazer.fabric.networking.payload.s2c;

import java.nio.charset.StandardCharsets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.trailblazer.api.PathData;
import com.trailblazer.fabric.TrailblazerFabricClient;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SharedPathPayload(PathData path) implements CustomPayload {
    public static final Id<SharedPathPayload> ID =
            new Id<>(Identifier.of(TrailblazerFabricClient.MOD_ID, "share_path"));
    private static final Gson GSON = new GsonBuilder().create();

    // Defensive cap: shared paths can be large, but should never be unbounded.
    private static final int MAX_JSON_BYTES = 1_048_576;

    public static final PacketCodec<RegistryByteBuf, SharedPathPayload> CODEC = PacketCodec.of(
            SharedPathPayload::write,
            SharedPathPayload::read
    );

    private static void write(SharedPathPayload payload, RegistryByteBuf buf) {
        String json = GSON.toJson(payload.path());
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        buf.writeVarInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static SharedPathPayload read(RegistryByteBuf buf) {
        int length = buf.readVarInt();
        if (length < 0 || length > MAX_JSON_BYTES || length > buf.readableBytes()) {
            throw new IllegalStateException("Invalid shared path payload length: " + length);
        }
        byte[] data = new byte[length];
        buf.readBytes(data);
        PathData path = GSON.fromJson(new String(data, StandardCharsets.UTF_8), PathData.class);
        if (path == null) {
            throw new IllegalStateException("Received empty shared path payload");
        }
        return new SharedPathPayload(path);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
