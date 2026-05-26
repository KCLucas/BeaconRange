package com.kclucas.beaconrange.client;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class BeaconRangeRenderer {

    private static final float[][] LEVEL_COLORS = {
            {1f, 1f, 1f},
            {0.4f, 0.8f, 1f},
            {0.4f, 1f, 0.5f},
            {1f, 0.85f, 0.3f},
            {1f, 0.4f, 0.4f},
    };

    public static void render(WorldRenderContext context, Box box, int level) {
        VertexConsumerProvider consumers = context.consumers();
        MatrixStack matrices = context.matrices();

        Vec3d cam = MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos();

        float r = LEVEL_COLORS[level][0];
        float g = LEVEL_COLORS[level][1];
        float b = LEVEL_COLORS[level][2];

        double x1 = box.minX - cam.x, y1 = box.minY - cam.y, z1 = box.minZ - cam.z;
        double x2 = box.maxX - cam.x, y2 = box.maxY - cam.y, z2 = box.maxZ - cam.z;

        matrices.push();
        Matrix4f m = matrices.peek().getPositionMatrix();

        VertexConsumer fill = consumers.getBuffer(RenderLayers.debugFilledBox());
        float fa = 0.15f;

        // Bottom
        quad(fill,m, x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, r,g,b,fa);
        quad(fill,m, x1,y1,z2, x2,y1,z2, x2,y1,z1, x1,y1,z1, r,g,b,fa);
        // Top
        quad(fill,m, x1,y2,z1, x1,y2,z2, x2,y2,z2, x2,y2,z1, r,g,b,fa);
        quad(fill,m, x2,y2,z1, x2,y2,z2, x1,y2,z2, x1,y2,z1, r,g,b,fa);
        // North
        quad(fill,m, x1,y1,z1, x1,y2,z1, x2,y2,z1, x2,y1,z1, r,g,b,fa);
        quad(fill,m, x2,y1,z1, x2,y2,z1, x1,y2,z1, x1,y1,z1, r,g,b,fa);
        // South
        quad(fill,m, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, r,g,b,fa);
        quad(fill,m, x1,y2,z2, x2,y2,z2, x2,y1,z2, x1,y1,z2, r,g,b,fa);
        // West
        quad(fill,m, x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, r,g,b,fa);
        quad(fill,m, x1,y2,z1, x1,y2,z2, x1,y1,z2, x1,y1,z1, r,g,b,fa);
        // East
        quad(fill,m, x2,y1,z1, x2,y2,z1, x2,y2,z2, x2,y1,z2, r,g,b,fa);
        quad(fill,m, x2,y1,z2, x2,y2,z2, x2,y2,z1, x2,y1,z1, r,g,b,fa);

        matrices.pop();
    }

    private static void quad(VertexConsumer v, Matrix4f m,
                             double ax,double ay,double az, double bx,double by,double bz,
                             double cx,double cy,double cz, double dx,double dy,double dz,
                             float r,float g,float b,float a) {
        v.vertex(m,(float)ax,(float)ay,(float)az).color(r,g,b,a);
        v.vertex(m,(float)bx,(float)by,(float)bz).color(r,g,b,a);
        v.vertex(m,(float)cx,(float)cy,(float)cz).color(r,g,b,a);
        v.vertex(m,(float)dx,(float)dy,(float)dz).color(r,g,b,a);
    }
}