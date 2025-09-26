package com.trailblazer.fabric.networking.payload.c2s;

import com.trailblazer.fabric.TrailblazerFabricClient;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.UUID;

public record SharePathWithPlayersPayload(UUID pathId, List<UUID> playerIds) implements CustomPayload {
    public static final Id<SharePathWithPlayersPayload> ID = new Id<>(Identifier.of(TrailblazerFabricClient.MOD_ID, "share_path_with_players"));
    public static final PacketCodec<RegistryByteBuf, SharePathWithPlayersPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeUuid(value.pathId());
                buf.writeCollection(value.playerIds(), (byteBuf, uuid) -> byteBuf.writeUuid(uuid));
            },
            buf -> new SharePathWithPlayersPayload(buf.readUuid(), buf.readList(RegistryByteBuf::readUuid))
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
