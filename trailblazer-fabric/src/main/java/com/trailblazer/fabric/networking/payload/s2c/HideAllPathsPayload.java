package com.trailblazer.fabric.networking.payload.s2c;

import com.trailblazer.fabric.TrailblazerFabricClient;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * A simple signal payload sent from the server to the client to instruct
 * it to hide all currently rendered paths.
 */
public record HideAllPathsPayload() implements CustomPayload {

    public static final Id<HideAllPathsPayload> ID = new Id<>(Identifier.of(TrailblazerFabricClient.MOD_ID, "hide_all_paths"));

    public static final PacketCodec<RegistryByteBuf, HideAllPathsPayload> CODEC = new PacketCodec<RegistryByteBuf, HideAllPathsPayload>() {
        @Override
        public HideAllPathsPayload decode(RegistryByteBuf buf) {
            // The server sends a single byte for legacy reasons. We must read it.
            if (buf.readableBytes() > 0) {
                buf.readByte();
            }
            return new HideAllPathsPayload();
        }

        @Override
        public void encode(RegistryByteBuf buf, HideAllPathsPayload value) {
            // Nothing to write.
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
