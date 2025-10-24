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
    private final long sequenceNumber;
    private final Long acknowledgedSequence;

    public PathActionResultPayload(String action, UUID pathId, boolean success, String message, PathData updated) {
        this(action, pathId, success, message, updated, 0L, null);
    }

    public PathActionResultPayload(String action, UUID pathId, boolean success, String message, PathData updated, long sequenceNumber, Long acknowledgedSequence) {
        this.action = action;
        this.pathId = pathId;
        this.success = success;
        this.message = message;
        this.updated = updated;
        this.sequenceNumber = sequenceNumber;
        this.acknowledgedSequence = acknowledgedSequence;
    }

    public byte[] toBytes() {
        // Encode to match the Fabric client codec exactly:
        // - Strings are prefixed with VarInt length (NOT fixed 4-byte int)
        // - Presence flags are single bytes (boolean)
        // - UUID is two longs
        // - Sequence and optional ack are longs

        String json = updated == null ? "" : GSON.toJson(updated);
        byte[] actionB = action.getBytes(StandardCharsets.UTF_8);
        byte[] msgB = (message == null ? "" : message).getBytes(StandardCharsets.UTF_8);
        byte[] updatedB = json.getBytes(StandardCharsets.UTF_8);

        int size = 0;
        size += sizeOfVarInt(actionB.length) + actionB.length; // action string (VarInt length)
        size += 1; // path id presence flag
        size += (pathId == null ? 0 : 16); // UUID (2 longs)
        size += 1; // success flag
        size += sizeOfVarInt(msgB.length) + msgB.length; // message string (VarInt length)
        size += 1; // updated presence flag
        size += (updated == null ? 0 : sizeOfVarInt(updatedB.length) + updatedB.length);
        size += 8; // sequence number
        size += 1; // ack presence flag
        size += (acknowledgedSequence == null ? 0 : 8);

        ByteBuffer buf = ByteBuffer.allocate(size);

        putVarString(buf, actionB);
        buf.put((byte) (pathId != null ? 1 : 0));
        if (pathId != null) {
            buf.putLong(pathId.getMostSignificantBits());
            buf.putLong(pathId.getLeastSignificantBits());
        }
        buf.put((byte) (success ? 1 : 0));
        putVarString(buf, msgB);
        buf.put((byte) (updated != null ? 1 : 0));
        if (updated != null) {
            putVarString(buf, updatedB);
        }
        buf.putLong(sequenceNumber);
        buf.put((byte) (acknowledgedSequence != null ? 1 : 0));
        if (acknowledgedSequence != null) {
            buf.putLong(acknowledgedSequence);
        }

        return buf.array();
    }

    private static void putVarString(ByteBuffer buf, byte[] data) {
        putVarInt(buf, data.length);
        buf.put(data);
    }

    private static void putVarInt(ByteBuffer buf, int value) {
        while ((value & -128) != 0) {
            buf.put((byte) (value & 0x7F | 0x80));
            value >>>= 7;
        }
        buf.put((byte) value);
    }

    private static int sizeOfVarInt(int value) {
        int size = 1;
        while ((value & -128) != 0) {
            size++;
            value >>>= 7;
        }
        return size;
    }
}
