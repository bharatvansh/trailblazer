package com.trailblazer.plugin.networking.payload.c2s;

import org.bukkit.NamespacedKey;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Client-to-Server payload requesting to start recording a path on the server.
 */
public class StartRecordingPayload {
    public static final NamespacedKey ID = new NamespacedKey("trailblazer", "start_recording_request");
    public static final String CHANNEL = ID.toString();
    
    private final String pathName;
    
    private StartRecordingPayload(String pathName) {
        this.pathName = pathName;
    }
    
    public String getPathName() {
        return pathName;
    }
    
    public static StartRecordingPayload fromBytes(byte[] data) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             DataInputStream in = new DataInputStream(bais)) {
            
            boolean hasName = in.readBoolean();
            String pathName = null;
            if (hasName) {
                int nameLength = in.readInt();
                byte[] nameBytes = new byte[nameLength];
                in.readFully(nameBytes);
                pathName = new String(nameBytes, StandardCharsets.UTF_8);
            }
            
            return new StartRecordingPayload(pathName);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize StartRecordingPayload", e);
        }
    }
}

