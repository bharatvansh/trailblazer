package com.trailblazer.fabric.networking.payload.s2c;

import com.trailblazer.api.PathData;
import com.trailblazer.fabric.TrailblazerFabricClient;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public record SharePathPayload(PathData pathData) implements CustomPayload {

    public static final Id<SharePathPayload> ID = new Id<>(Identifier.of(TrailblazerFabricClient.MOD_ID, "share_path"));
    public static final PacketCodec<RegistryByteBuf, SharePathPayload> CODEC = PacketCodec.of(
        (value, buf) -> {
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                 ObjectOutputStream oos = new ObjectOutputStream(bos)) {
                oos.writeObject(value.pathData);
                byte[] bytes = bos.toByteArray();
                buf.writeInt(bytes.length);
                buf.writeBytes(bytes);
            } catch (IOException e) {
                throw new RuntimeException("Failed to serialize PathData", e);
            }
        },
        (buf) -> {
            int length = buf.readInt();
            byte[] bytes = new byte[length];
            buf.readBytes(bytes);
            try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
                 ObjectInputStream ois = new ObjectInputStream(bis)) {
                PathData pathData = (PathData) ois.readObject();
                return new SharePathPayload(pathData);
            } catch (IOException | ClassNotFoundException e) {
                throw new RuntimeException("Failed to deserialize PathData", e);
            }
        }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}