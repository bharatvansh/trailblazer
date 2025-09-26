package com.trailblazer.plugin.networking.payload.s2c;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.trailblazer.api.PathData;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class SharePathPayload {

    public static final String CHANNEL_NAME = "trailblazer:share_path";
    private static final Gson GSON = new GsonBuilder().create();

    private final PathData pathData;

    public SharePathPayload(PathData pathData) {
        this.pathData = pathData;
    }

    public PathData getPathData() {
        return pathData;
    }

    public byte[] toBytes() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] jsonBytes = GSON.toJson(pathData).getBytes(StandardCharsets.UTF_8);
        writeVarInt(bos, jsonBytes.length);
        bos.writeBytes(jsonBytes);
        return bos.toByteArray();
    }

    public static SharePathPayload fromBytes(byte[] bytes) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes)) {
            int len = readVarInt(bis);
            byte[] data = bis.readNBytes(len);
            PathData path = GSON.fromJson(new String(data, StandardCharsets.UTF_8), PathData.class);
            return new SharePathPayload(path);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize shared path payload", e);
        }
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        while ((value & -128) != 0) {
            out.write(value & 0x7F | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private static int readVarInt(ByteArrayInputStream in) {
        int numRead = 0;
        int result = 0;
        int read;
        do {
            read = in.read();
            if (read == -1) {
                throw new IllegalStateException("Unexpected end of stream while reading VarInt");
            }
            int value = (read & 0x7F);
            result |= (value << (7 * numRead));

            numRead++;
            if (numRead > 5) {
                throw new IllegalStateException("VarInt too big");
            }
        } while ((read & 0x80) != 0);

        return result;
    }
}