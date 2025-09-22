package com.trailblazer.fabric.rendering;

import com.mojang.blaze3d.systems.RenderSystem;
import com.trailblazer.api.PathData;
import com.trailblazer.fabric.ClientPathManager;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Handles the client-side rendering of paths in the world.
 */
public class PathRenderer {

    private final ClientPathManager pathManager;

    public PathRenderer(ClientPathManager pathManager) {
        this.pathManager = pathManager;
    }

    public void initialize() {
        // Register our render method to be called after entities are rendered in the world.
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            // Get the camera's position to correctly offset our rendering
            Camera camera = context.camera();
            Vec3d cameraPos = camera.getPos();

            // Matrix stack (Yarn name MatrixStack) manages transformations
            MatrixStack matrices = context.matrixStack();
            matrices.push();
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            Matrix4f positionMatrix = matrices.peek().getPositionMatrix();

            // Prepare rendering state (wrapped in try/finally to guarantee restoration)
            float lineWidth = 3.0F;
            if (lineWidth < 1.0F) lineWidth = 1.0F;
            if (lineWidth > 10.0F) lineWidth = 10.0F; // avoid absurd values

            RenderSystem.lineWidth(lineWidth);
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            try {

                VertexConsumer lineConsumer = context.consumers().getBuffer(RenderLayer.getLines());
                // For each path, emit line segment vertices including a normal so the vertex format is satisfied.
                for (PathData path : pathManager.getVisiblePaths()) {
                    var points = path.getPoints();
                    if (points == null || points.size() < 2) continue;
                    com.trailblazer.api.Vector3d prev = points.get(0);
                    for (int i = 1; i < points.size(); i++) {
                        com.trailblazer.api.Vector3d cur = points.get(i);
                        float x1 = (float) prev.getX();
                        float y1 = (float) prev.getY();
                        float z1 = (float) prev.getZ();
                        float x2 = (float) cur.getX();
                        float y2 = (float) cur.getY();
                        float z2 = (float) cur.getZ();

                        // Compute a direction vector to use as a normal. (Lines don't really need lighting, but
                        // the LINES vertex format in 1.21 includes a normal attribute; omitting it crashes.)
                        float dx = x2 - x1;
                        float dy = y2 - y1;
                        float dz = z2 - z1;
                        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                        float nx, ny, nz;
                        if (len > 1.0e-6f) {
                            nx = dx / len;
                            ny = dy / len;
                            nz = dz / len;
                        } else {
                            // Fallback normal (arbitrary but valid) if points coincide
                            nx = 0f; ny = 1f; nz = 0f;
                        }

            lineConsumer.vertex(positionMatrix, x1, y1, z1)
                .color(0, 255, 255, 255)
                .normal(nx, ny, nz);
            lineConsumer.vertex(positionMatrix, x2, y2, z2)
                .color(0, 255, 255, 255)
                .normal(nx, ny, nz);
                        prev = cur;
                    }
                }
            } finally {
                // Restore render state
                RenderSystem.depthMask(true);
                RenderSystem.enableCull();
                matrices.pop();
            }
        });
    }
}

