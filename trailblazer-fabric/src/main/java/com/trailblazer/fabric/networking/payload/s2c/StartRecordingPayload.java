package com.trailblazer.fabric.networking.payload.s2c;

import com.trailblazer.fabric.TrailblazerFabricClient;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Server-to-Client payload indicating that recording has started on the server.
 * Instructs the client to update UI state and prepare for live path rendering.
 * 
 * Note: This payload uses custom binary format to match Bukkit server's plugin message format:
 * - UUID: 16 bytes (2 longs)
 * - String: 4 bytes (int length) + UTF-8 bytes
 */
public record StartRecordingPayload(UUID pathId, String pathName, String dimension) implements CustomPayload {
    public static final Id<StartRecordingPayload> ID = new Id<>(Identifier.of(TrailblazerFabricClient.MOD_ID, "start_recording"));
    
    public static final PacketCodec<RegistryByteBuf, StartRecordingPayload> CODEC = PacketCodec.of(
        (value, buf) -> {
            // Write in Fabric format for Fabric-to-Fabric communication
            buf.writeUuid(value.pathId);
            buf.writeString(value.pathName);
            buf.writeString(value.dimension);
        },
        (buf) -> {
            // Read in Bukkit plugin message format (compatible with server)
            try {
                // Read UUID as two longs (16 bytes)
                long msb = buf.readLong();
                long lsb = buf.readLong();
                UUID pathId = new UUID(msb, lsb);
                
                // Read pathName: int length + UTF-8 bytes
                int nameLength = buf.readInt();
                byte[] nameBytes = new byte[nameLength];
                buf.readBytes(nameBytes);
                String pathName = new String(nameBytes, StandardCharsets.UTF_8);
                
                // Read dimension: int length + UTF-8 bytes
                int dimLength = buf.readInt();
                byte[] dimBytes = new byte[dimLength];
                buf.readBytes(dimBytes);
                String dimension = new String(dimBytes, StandardCharsets.UTF_8);
                
                return new StartRecordingPayload(pathId, pathName, dimension);
            } catch (Exception e) {
                TrailblazerFabricClient.LOGGER.error("Failed to decode StartRecordingPayload from server", e);
                throw new RuntimeException("Failed to decode StartRecordingPayload", e);
            }
        }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}

