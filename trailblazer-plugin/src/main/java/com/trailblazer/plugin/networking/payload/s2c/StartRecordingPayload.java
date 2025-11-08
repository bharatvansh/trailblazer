package com.trailblazer.plugin.networking.payload.s2c;

import org.bukkit.NamespacedKey;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Server-to-Client payload indicating that recording has started on the server.
 * Instructs the client to update UI state and prepare for live path rendering.
 */
public class StartRecordingPayload {
    public static final NamespacedKey ID = new NamespacedKey("trailblazer", "start_recording");
    public static final String CHANNEL = ID.toString();
    
    private final UUID pathId;
    private final String pathName;
    private final String dimension;
    
    public StartRecordingPayload(UUID pathId, String pathName, String dimension) {
        this.pathId = pathId;
        this.pathName = pathName;
        this.dimension = dimension;
    }
    
    public byte[] toBytes() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(baos)) {
            
            // Write UUID as two longs
            out.writeLong(pathId.getMostSignificantBits());
            out.writeLong(pathId.getLeastSignificantBits());
            
            // Write pathName
            byte[] nameBytes = pathName.getBytes(StandardCharsets.UTF_8);
            out.writeInt(nameBytes.length);
            out.write(nameBytes);
            
            // Write dimension
            byte[] dimBytes = dimension.getBytes(StandardCharsets.UTF_8);
            out.writeInt(dimBytes.length);
            out.write(dimBytes);
            
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize StartRecordingPayload", e);
        }
    }
}

