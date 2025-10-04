package com.trailblazer.fabric.networking.payload.c2s;

import com.trailblazer.fabric.TrailblazerFabricClient;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Sends a locally recorded PathData JSON to the server for persistence.
 */
public record SavePathRequestPayload(String pathJson) implements CustomPayload {

    public static final Id<SavePathRequestPayload> ID =
            new Id<>(Identifier.of(TrailblazerFabricClient.MOD_ID, "save_path"));

    public static final PacketCodec<RegistryByteBuf, SavePathRequestPayload> CODEC = PacketCodec.of(
            SavePathRequestPayload::write,
            SavePathRequestPayload::read
    );

    private static void write(SavePathRequestPayload payload, RegistryByteBuf buf) {
        buf.writeString(payload.pathJson());
    }

    private static SavePathRequestPayload read(RegistryByteBuf buf) {
        String json = buf.readString();
        return new SavePathRequestPayload(json);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
