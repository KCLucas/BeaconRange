package com.kclucas.beaconrange.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Zeichnet die Bereiche: halbtransparente Wände (unten kräftig, über dem Beacon nach oben ausblendend), leuchtende
 * Kanten und eine Linie rundherum auf Höhe des Spielers.
 */
public final class RangeRenderer {

	private static final float EDGE_WIDTH = 2.5F;
	private static final float HEIGHT_LINE_WIDTH = 3.5F;
	/** So kräftig bleiben die Kanten über dem ausgeblendeten Teil - der Bereich geht ja bis in den Himmel. */
	private static final float FAINT = 0.3F;
	/**
	 * Außenseiten der Wände schwächer als die Innenseiten: von außen schaut man sonst durch zwei Wände und alles
	 * dahinter wird trüb, von innen sieht man nur eine und die soll deutlich sein.
	 */
	private static final float OUTSIDE = 0.35F;
	/** Die Linie auf Spielerhöhe erscheint im Bereich und bis so viele Blöcke davor - dort sucht man die Grenze. */
	private static final double HEIGHT_LINE_REACH = 12.0;

	private RangeRenderer() {
	}

	public static void render(LevelRenderContext context, List<BeaconArea> areas) {
		PoseStack poseStack = context.poseStack();
		SubmitNodeCollector collector = context.submitNodeCollector();
		Minecraft client = Minecraft.getInstance();
		if (areas.isEmpty() || client.player == null) {
			return;
		}
		BeaconRangeConfig config = BeaconRangeClient.getConfig();
		Vec3 camera = context.levelState().cameraRenderState.pos;
		Player player = client.player;
		float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		// knapp über den Füßen: zeigt die Grenze am Boden, auf dem man steht
		double lineY = Mth.lerp(partialTick, player.yo, player.getY()) + 0.1;
		double playerX = Mth.lerp(partialTick, player.xo, player.getX());
		double playerZ = Mth.lerp(partialTick, player.zo, player.getZ());
		// Weit entfernte angepinnte Beacons nicht quer über die Karte zeichnen.
		double maxDistance = client.options.getEffectiveRenderDistance() * 16.0 + 64.0;

		// Gezeichnet wird später gesammelt; die Listen hier hängen nur die Ecken an.
		if (config.walls != BeaconRangeConfig.WallStyle.OFF) {
			collector.submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, fill) -> {
				Matrix4f matrix = pose.pose();
				for (BeaconArea area : areas) {
					if (near(area, camera, maxDistance)) {
						walls(fill, matrix, area, camera, config);
					}
				}
			});
		}

		// linesTranslucent: die normalen Linien ignorieren in 26.x die Deckkraft, die Kanten blendeten sonst nicht aus.
		collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, lines) -> {
			for (BeaconArea area : areas) {
				if (!near(area, camera, maxDistance)) {
					continue;
				}
				if (config.edges) {
					edges(lines, pose, area, camera, config.walls == BeaconRangeConfig.WallStyle.FULL);
				}
				if (config.heightLine && showHeightLine(area, lineY, playerX, playerZ)) {
					ring(lines, pose, area.box(), lineY, camera, argb(area.color(), 1.0F), HEIGHT_LINE_WIDTH);
				}
			}
		});
	}

	/**
	 * Die Linie auf Spielerhöhe zeigt, wo der Bereich genau da endet, wo man ist - also nur im Bereich oder kurz davor.
	 * Von weiter weg (etwa beim Überblick von oben) hinge sie nur als Strich am Horizont.
	 */
	private static boolean showHeightLine(BeaconArea area, double y, double x, double z) {
		if (y <= area.floor() || y >= area.ceiling()) {
			return false;
		}
		AABB box = area.box();
		double dx = Math.max(0, Math.max(box.minX - x, x - box.maxX));
		double dz = Math.max(0, Math.max(box.minZ - z, z - box.maxZ));
		return dx * dx + dz * dz <= HEIGHT_LINE_REACH * HEIGHT_LINE_REACH;
	}

	private static boolean near(BeaconArea area, Vec3 camera, double maxDistance) {
		AABB box = area.box();
		double dx = Math.max(0, Math.max(box.minX - camera.x, camera.x - box.maxX));
		double dz = Math.max(0, Math.max(box.minZ - camera.z, camera.z - box.maxZ));
		return dx * dx + dz * dz < maxDistance * maxDistance;
	}

	private static void walls(VertexConsumer fill, Matrix4f m, BeaconArea area, Vec3 camera, BeaconRangeConfig config) {
		AABB box = area.box();
		float alpha = config.opacity.alpha;
		if (config.walls == BeaconRangeConfig.WallStyle.FULL) {
			band(fill, m, box, area.floor(), area.ceiling(), camera, area.color(), alpha, alpha);
			return;
		}
		double fadeStart = Math.max(area.floor(), area.fadeStart());
		double fadeEnd = Math.min(area.ceiling(), fadeStart + area.fadeHeight());
		band(fill, m, box, area.floor(), fadeStart, camera, area.color(), alpha, alpha);
		band(fill, m, box, fadeStart, fadeEnd, camera, area.color(), alpha, 0.0F);
	}

	/** Die vier Seitenwände zwischen zwei Höhen, Deckkraft unten und oben (dazwischen weicher Übergang). */
	private static void band(VertexConsumer v, Matrix4f m, AABB box, double fromY, double toY, Vec3 camera,
			int rgb, float bottomAlpha, float topAlpha) {
		if (toY <= fromY) {
			return;
		}
		int bottom = argb(rgb, bottomAlpha), top = argb(rgb, topAlpha);
		int outsideBottom = argb(rgb, bottomAlpha * OUTSIDE), outsideTop = argb(rgb, topAlpha * OUTSIDE);
		float x1 = (float) (box.minX - camera.x), x2 = (float) (box.maxX - camera.x);
		float z1 = (float) (box.minZ - camera.z), z2 = (float) (box.maxZ - camera.z);
		float y1 = (float) (fromY - camera.y), y2 = (float) (toY - camera.y);
		wall(v, m, x1, z1, x2, z1, y1, y2, bottom, top, outsideBottom, outsideTop); // Norden
		wall(v, m, x2, z1, x2, z2, y1, y2, bottom, top, outsideBottom, outsideTop); // Osten
		wall(v, m, x2, z2, x1, z2, y1, y2, bottom, top, outsideBottom, outsideTop); // Süden
		wall(v, m, x1, z2, x1, z1, y1, y2, bottom, top, outsideBottom, outsideTop); // Westen
	}

	/**
	 * Eine senkrechte Fläche, einmal pro Seite: Minecraft zeichnet nur die Vorderseite eines Vierecks. Die Ecken
	 * laufen im Uhrzeigersinn um den Bereich (von oben gesehen), dadurch zeigt die erste Fläche nach innen.
	 */
	private static void wall(VertexConsumer v, Matrix4f m, float ax, float az, float bx, float bz,
			float y1, float y2, int bottom, int top, int outsideBottom, int outsideTop) {
		v.addVertex(m, ax, y1, az).setColor(bottom);
		v.addVertex(m, bx, y1, bz).setColor(bottom);
		v.addVertex(m, bx, y2, bz).setColor(top);
		v.addVertex(m, ax, y2, az).setColor(top);

		v.addVertex(m, ax, y2, az).setColor(outsideTop);
		v.addVertex(m, bx, y2, bz).setColor(outsideTop);
		v.addVertex(m, bx, y1, bz).setColor(outsideBottom);
		v.addVertex(m, ax, y1, az).setColor(outsideBottom);
	}

	/** Kanten: unten voll, senkrecht über dem Beacon ausblendend und schwach weiter bis ganz oben. */
	private static void edges(VertexConsumer v, PoseStack.Pose pose, BeaconArea area, Vec3 camera, boolean full) {
		AABB box = area.box();
		int strong = argb(area.color(), 1.0F);
		int faint = argb(area.color(), full ? 1.0F : FAINT);
		ring(v, pose, box, area.floor(), camera, strong, EDGE_WIDTH);
		ring(v, pose, box, area.ceiling(), camera, faint, EDGE_WIDTH);

		double fadeStart = Math.max(area.floor(), area.fadeStart());
		double fadeEnd = Math.min(area.ceiling(), fadeStart + area.fadeHeight());
		double[][] corners = {{box.minX, box.minZ}, {box.maxX, box.minZ}, {box.maxX, box.maxZ}, {box.minX, box.maxZ}};
		for (double[] c : corners) {
			float x = (float) (c[0] - camera.x), z = (float) (c[1] - camera.z);
			line(v, pose, x, (float) (area.floor() - camera.y), z, x, (float) (fadeStart - camera.y), z, strong, strong, EDGE_WIDTH);
			line(v, pose, x, (float) (fadeStart - camera.y), z, x, (float) (fadeEnd - camera.y), z, strong, faint, EDGE_WIDTH);
			line(v, pose, x, (float) (fadeEnd - camera.y), z, x, (float) (area.ceiling() - camera.y), z, faint, faint, EDGE_WIDTH);
		}
	}

	/** Waagerechtes Rechteck um den Bereich auf einer Höhe. */
	private static void ring(VertexConsumer v, PoseStack.Pose pose, AABB box, double y, Vec3 camera, int color, float width) {
		float x1 = (float) (box.minX - camera.x), x2 = (float) (box.maxX - camera.x);
		float z1 = (float) (box.minZ - camera.z), z2 = (float) (box.maxZ - camera.z);
		float ry = (float) (y - camera.y);
		line(v, pose, x1, ry, z1, x2, ry, z1, color, color, width);
		line(v, pose, x2, ry, z1, x2, ry, z2, color, color, width);
		line(v, pose, x2, ry, z2, x1, ry, z2, color, color, width);
		line(v, pose, x1, ry, z2, x1, ry, z1, color, color, width);
	}

	private static void line(VertexConsumer v, PoseStack.Pose pose, float x1, float y1, float z1,
			float x2, float y2, float z2, int from, int to, float width) {
		float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
		float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
		if (length < 1.0E-4F) {
			return;
		}
		dx /= length;
		dy /= length;
		dz /= length;
		v.addVertex(pose, x1, y1, z1).setColor(from).setNormal(pose, dx, dy, dz).setLineWidth(width);
		v.addVertex(pose, x2, y2, z2).setColor(to).setNormal(pose, dx, dy, dz).setLineWidth(width);
	}

	static int argb(int rgb, float alpha) {
		return (Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255) << 24) | (rgb & 0xFFFFFF);
	}
}
