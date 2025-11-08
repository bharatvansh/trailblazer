package com.trailblazer.plugin.networking.payload.c2s;

import org.bukkit.NamespacedKey;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * Client-to-Server payload requesting to stop recording a path on the server.
 */
public class StopRecordingPayload {
    public static final NamespacedKey ID = new NamespacedKey("trailblazer", "stop_recording_request");
    public static final String CHANNEL = ID.toString();
    
    private final boolean save;
    
    private StopRecordingPayload(boolean save) {
        this.save = save;
    }
    
    public boolean shouldSave() {
        return save;
    }
    
    public static StopRecordingPayload fromBytes(byte[] data) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             DataInputStream in = new DataInputStream(bais)) {
            
            boolean save = in.readBoolean();
            return new StopRecordingPayload(save);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize StopRecordingPayload", e);
        }
    }
}

