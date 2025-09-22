package com.trailblazer.fabric.networking.payload.c2s;

import com.trailblazer.fabric.TrailblazerFabricClient;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * A simple signal payload sent once by the client upon joining a server
 * to announce that it has the Trailblazer mod installed.
 */
public record HandshakePayload() implements CustomPayload {

    public static final Id<HandshakePayload> ID = new Id<>(Identifier.of(TrailblazerFabricClient.MOD_ID, "handshake"));

    public static final PacketCodec<RegistryByteBuf, HandshakePayload> CODEC = PacketCodec.unit(new HandshakePayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}

