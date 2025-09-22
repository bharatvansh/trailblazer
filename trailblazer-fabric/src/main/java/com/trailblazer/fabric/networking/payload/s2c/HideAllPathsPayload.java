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

    public static final PacketCodec<RegistryByteBuf, HideAllPathsPayload> CODEC = PacketCodec.unit(new HideAllPathsPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
