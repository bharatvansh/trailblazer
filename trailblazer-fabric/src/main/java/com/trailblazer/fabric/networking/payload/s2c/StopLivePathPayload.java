package com.trailblazer.fabric.networking.payload.s2c;

import com.trailblazer.fabric.TrailblazerFabricClient;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record StopLivePathPayload() implements CustomPayload {
    public static final Id<StopLivePathPayload> ID = new Id<>(Identifier.of(TrailblazerFabricClient.MOD_ID, "stop_live_path"));
    public static final PacketCodec<RegistryByteBuf, StopLivePathPayload> CODEC = PacketCodec.of(
        (value, buf) -> {},
        (buf) -> new StopLivePathPayload()
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}