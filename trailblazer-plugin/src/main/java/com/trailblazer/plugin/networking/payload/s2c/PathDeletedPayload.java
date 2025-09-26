package com.trailblazer.plugin.networking.payload.s2c;

import com.trailblazer.api.PathData;
import com.trailblazer.plugin.TrailblazerPlugin;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;

public class PathDeletedPayload {
    public static final String CHANNEL_NAME = "trailblazer:path_deleted";
    private final UUID pathId;

    public PathDeletedPayload(UUID pathId) {
        this.pathId = pathId;
    }

    public byte[] toBytes() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeLong(pathId.getMostSignificantBits());
        buf.writeLong(pathId.getLeastSignificantBits());
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }
}
