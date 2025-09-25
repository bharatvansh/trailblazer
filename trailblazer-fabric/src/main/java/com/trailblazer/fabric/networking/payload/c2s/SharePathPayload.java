package com.trailblazer.fabric.networking.payload.c2s;

import com.trailblazer.api.PathData;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record SharePathPayload(UUID pathId) implements CustomPayload {
    public static final CustomPayload.Id<SharePathPayload> ID = new CustomPayload.Id<>(Identifier.of("trailblazer", "share_path"));
    public static final PacketCodec<ByteBuf, SharePathPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeLong(value.pathId.getMostSignificantBits()).writeLong(value.pathId.getLeastSignificantBits()),
            buf -> new SharePathPayload(new UUID(buf.readLong(), buf.readLong()))
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
