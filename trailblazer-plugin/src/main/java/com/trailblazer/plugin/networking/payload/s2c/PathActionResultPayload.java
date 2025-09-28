package com.trailblazer.plugin.networking.payload.s2c;

import com.google.gson.Gson;
import com.trailblazer.api.PathData;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Plugin messaging (Bukkit) variant of the action result payload. */
public class PathActionResultPayload {
    public static final String CHANNEL = "trailblazer:path_action_result";
    private static final Gson GSON = new Gson();

    private final String action;
    private final UUID pathId;
    private final boolean success;
    private final String message;
    private final PathData updated;

    public PathActionResultPayload(String action, UUID pathId, boolean success, String message, PathData updated) {
        this.action = action;
        this.pathId = pathId;
        this.success = success;
        this.message = message;
        this.updated = updated;
    }

    public byte[] toBytes() {
        String json = updated == null ? "" : GSON.toJson(updated);
        byte[] actionB = action.getBytes(StandardCharsets.UTF_8);
        byte[] msgB = (message == null ? "" : message).getBytes(StandardCharsets.UTF_8);
        byte[] updatedB = json.getBytes(StandardCharsets.UTF_8);
        
        int size = 4 + actionB.length + 1 + (pathId == null ? 0 : 16) + 1 + 4 + msgB.length + 1 + 4 + updatedB.length;
        ByteBuffer buf = ByteBuffer.allocate(size);
        
        putVarString(buf, action);
        buf.put((byte)(pathId != null ? 1 : 0));
        if (pathId != null) {
            buf.putLong(pathId.getMostSignificantBits());
            buf.putLong(pathId.getLeastSignificantBits());
        }
        buf.put((byte)(success ? 1 : 0));
        putVarString(buf, message == null ? "" : message);
        buf.put((byte)(updated != null ? 1 : 0));
        if (updated != null) {
            putVarString(buf, json);
        }
        
        return buf.array();
    }

    private static void putVarString(ByteBuffer buf, String s) {
        byte[] data = s.getBytes(StandardCharsets.UTF_8);
        buf.putInt(data.length);
        buf.put(data);
    }
}
