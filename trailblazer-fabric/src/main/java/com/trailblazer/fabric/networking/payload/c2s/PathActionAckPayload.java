package com.trailblazer.fabric.networking.payload.c2s;

import com.trailblazer.fabric.TrailblazerFabricClient;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client-to-server acknowledgement for {@code PathActionResultPayload} messages.
 * Carries the highest sequence number the client has fully processed so the server
 * can stop retrying older messages.
 */
public record PathActionAckPayload(long acknowledgedSequence) implements CustomPayload {

    public static final Id<PathActionAckPayload> ID = new Id<>(Identifier.of(TrailblazerFabricClient.MOD_ID, "path_action_ack"));
    public static final PacketCodec<RegistryByteBuf, PathActionAckPayload> CODEC = PacketCodec.of(
            PathActionAckPayload::write,
            PathActionAckPayload::read
    );

    private static void write(PathActionAckPayload payload, RegistryByteBuf buf) {
        buf.writeLong(payload.acknowledgedSequence());
    }

    private static PathActionAckPayload read(RegistryByteBuf buf) {
        return new PathActionAckPayload(buf.readLong());
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
