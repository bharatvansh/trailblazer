package com.trailblazer.plugin.networking.payload.s2c;

import com.trailblazer.api.PathData;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class SharePathPayload {

    public static final String CHANNEL_NAME = "trailblazer:share_path";

    private final PathData pathData;

    public SharePathPayload(PathData pathData) {
        this.pathData = pathData;
    }

    public PathData getPathData() {
        return pathData;
    }

    public byte[] toBytes() {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(pathData);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize PathData", e);
        }
    }

    public static SharePathPayload fromBytes(byte[] bytes) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return new SharePathPayload((PathData) ois.readObject());
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Failed to deserialize PathData", e);
        }
    }
}