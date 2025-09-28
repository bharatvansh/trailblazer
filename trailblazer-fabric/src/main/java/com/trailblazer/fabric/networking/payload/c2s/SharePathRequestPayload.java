package com.trailblazer.fabric.networking.payload.c2s;

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

    private static void write(SharePathRequestPayload payload, RegistryByteBuf buf) {
        List<UUID> ids = payload.recipients();
        buf.writeVarInt(ids.size());
        for (UUID id : ids) {
            buf.writeUuid(id);
        }
        buf.writeString(payload.pathJson());
    }

    private static SharePathRequestPayload read(RegistryByteBuf buf) {
        int count = buf.readVarInt();
        List<UUID> recipients = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            recipients.add(buf.readUuid());
        }
        String pathJson = buf.readString();
        return new SharePathRequestPayload(recipients, pathJson);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
