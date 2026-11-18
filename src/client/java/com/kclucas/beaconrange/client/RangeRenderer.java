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
 * Zeichnet die Bereiche: bei Beacons halbtransparente Wände (unten kräftig, über dem Beacon nach oben ausblendend),
 * leuchtende Kanten und eine Linie rundherum auf Höhe des Spielers; bei Conduits eine Kugel mit Gitter, dem Kreis auf
 * Spielerhöhe und bei vollem Rahmen dem Würfel, in dem er Monster angreift.
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
	/** So fein ist die Conduit-Kugel unterteilt - wirkt auch bei 96 Blöcken Radius rund. */
	private static final int SPHERE_STACKS = 32;
	private static final int SPHERE_SLICES = 64;
	/** Die Kugel hat viel mehr Fläche als ein Kasten - etwas zurückhaltender, sonst färbt sie alles ein. */
	private static final float SPHERE_ALPHA = 0.7F;
	/** Breitenkreise und Längslinien der Kugel: schwächer und dünner als der Äquator. */
	private static final float GRID = 0.45F;
	private static final float GRID_WIDTH = 1.5F;
	/** Würfel, in dem ein voller Conduit Monster angreift: rot. */
	private static final int ATTACK_COLOR = 0xFF5C5C;

	private RangeRenderer() {
	}

	public static void render(LevelRenderContext context, List<BeaconArea> areas, List<ConduitArea> conduits) {
		PoseStack poseStack = context.poseStack();
		SubmitNodeCollector collector = context.submitNodeCollector();
		Minecraft client = Minecraft.getInstance();
		if (areas.isEmpty() && conduits.isEmpty() || client.player == null) {
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
		// Nur bis zur Sichtweite: dahinter liegt Nebel, die Wände würden ungetrübt aus ihm herausleuchten.
		double maxDistance = client.options.getEffectiveRenderDistance() * 16.0;

		// Gezeichnet wird später gesammelt; die Listen hier hängen nur die Ecken an.
		if (config.walls != BeaconRangeConfig.WallStyle.OFF) {
			collector.submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, fill) -> {
				Matrix4f matrix = pose.pose();
				for (BeaconArea area : areas) {
					if (near(area, camera, maxDistance)) {
						walls(fill, matrix, area, camera, config);
					}
				}
				for (ConduitArea conduit : conduits) {
					if (near(conduit, camera, maxDistance)) {
						sphere(fill, matrix, conduit, camera, config.opacity.alpha * SPHERE_ALPHA);
					}
				}
			});
		}

		// 26.1/26.2: lines() blendet schon und schreibt Tiefe (wie linesTranslucent in 26.3). linesTranslucent schreibt hier
		// keine Tiefe - dann überdecken Wasser und Glas dahinter die Kanten. Beim Abgleich mit 26.3 nicht zurückdrehen.
		collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, lines) -> {
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
			for (ConduitArea conduit : conduits) {
				if (!near(conduit, camera, maxDistance)) {
					continue;
				}
				if (config.edges) {
					grid(lines, pose, conduit, camera);
				}
				if (config.heightLine) {
					heightCircle(lines, pose, conduit, lineY, playerX, playerZ, camera);
				}
				if (config.conduitAttack && conduit.attacks()) {
					cube(lines, pose, Conduits.attackArea(conduit.pos()), camera, argb(ATTACK_COLOR, 1.0F), EDGE_WIDTH);
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

	private static boolean near(ConduitArea conduit, Vec3 camera, double maxDistance) {
		double dx = Math.max(0, Math.abs(conduit.centerX() - camera.x) - conduit.range());
		double dz = Math.max(0, Math.abs(conduit.centerZ() - camera.z) - conduit.range());
		return dx * dx + dz * dz < maxDistance * maxDistance;
	}

	// ------------------------------------------------------------------ Conduit: Kugel

	/** Punkte der Einheitskugel: Breitenkreise von oben (0) nach unten, Längen rundherum - einmal ausgerechnet. */
	private static final float[] STACK_Y = new float[SPHERE_STACKS + 1];
	private static final float[] STACK_R = new float[SPHERE_STACKS + 1];
	private static final float[] SLICE_X = new float[SPHERE_SLICES + 1];
	private static final float[] SLICE_Z = new float[SPHERE_SLICES + 1];

	static {
		for (int i = 0; i <= SPHERE_STACKS; i++) {
			double theta = Math.PI * i / SPHERE_STACKS;
			STACK_Y[i] = (float) Math.cos(theta);
			STACK_R[i] = (float) Math.sin(theta);
		}
		for (int j = 0; j <= SPHERE_SLICES; j++) {
			double phi = 2 * Math.PI * j / SPHERE_SLICES;
			SLICE_X[j] = (float) Math.cos(phi);
			SLICE_Z[j] = (float) Math.sin(phi);
		}
	}

	/**
	 * Halbtransparente Kugel. Jedes Feld nur von der Seite, auf der die Kamera ist: von außen schwach (sonst wird alles
	 * dahinter trüb), von innen deutlich - in dieser Reihenfolge zeigt es nach außen, umgekehrt nach innen.
	 */
	private static void sphere(VertexConsumer v, Matrix4f m, ConduitArea conduit, Vec3 camera, float alpha) {
		float cx = (float) (conduit.centerX() - camera.x), cy = (float) (conduit.centerY() - camera.y);
		float cz = (float) (conduit.centerZ() - camera.z), r = conduit.range();
		int inside = argb(conduit.color(), alpha), outside = argb(conduit.color(), alpha * OUTSIDE);
		for (int i = 0; i < SPHERE_STACKS; i++) {
			float topY = cy + r * STACK_Y[i], bottomY = cy + r * STACK_Y[i + 1];
			float topR = r * STACK_R[i], bottomR = r * STACK_R[i + 1];
			for (int j = 0; j < SPHERE_SLICES; j++) {
				float ax = cx + topR * SLICE_X[j], az = cz + topR * SLICE_Z[j];
				float bx = cx + topR * SLICE_X[j + 1], bz = cz + topR * SLICE_Z[j + 1];
				float qx = cx + bottomR * SLICE_X[j + 1], qz = cz + bottomR * SLICE_Z[j + 1];
				float dx = cx + bottomR * SLICE_X[j], dz = cz + bottomR * SLICE_Z[j];
				// Mitte des Feldes; die Kamera liegt im Ursprung. Außen, wenn sie in Richtung der Außennormale liegt.
				float mx = (ax + bx + qx + dx) / 4, my = (topY + bottomY) / 2, mz = (az + bz + qz + dz) / 4;
				boolean outsideView = (mx - cx) * -mx + (my - cy) * -my + (mz - cz) * -mz > 0;
				if (outsideView) {
					v.addVertex(m, ax, topY, az).setColor(outside);
					v.addVertex(m, bx, topY, bz).setColor(outside);
					v.addVertex(m, qx, bottomY, qz).setColor(outside);
					v.addVertex(m, dx, bottomY, dz).setColor(outside);
				} else {
					v.addVertex(m, dx, bottomY, dz).setColor(inside);
					v.addVertex(m, qx, bottomY, qz).setColor(inside);
					v.addVertex(m, bx, topY, bz).setColor(inside);
					v.addVertex(m, ax, topY, az).setColor(inside);
				}
			}
		}
	}

	/** Gitter auf der Kugel: Äquator kräftig, dazu Breitenkreise und Längslinien schwächer. */
	private static void grid(VertexConsumer v, PoseStack.Pose pose, ConduitArea conduit, Vec3 camera) {
		int strong = argb(conduit.color(), 1.0F), faint = argb(conduit.color(), GRID);
		float cx = (float) (conduit.centerX() - camera.x), cy = (float) (conduit.centerY() - camera.y);
		float cz = (float) (conduit.centerZ() - camera.z), r = conduit.range();
		for (int ring = 1; ring < 6; ring++) {
			int i = SPHERE_STACKS * ring / 6;
			boolean equator = ring == 3;
			circle(v, pose, cx, cy + r * STACK_Y[i], cz, r * STACK_R[i], equator ? strong : faint,
					equator ? EDGE_WIDTH : GRID_WIDTH);
		}
		for (int meridian = 0; meridian < 8; meridian++) {
			int j = SPHERE_SLICES * meridian / 8;
			for (int i = 0; i < SPHERE_STACKS; i++) {
				line(v, pose, cx + r * STACK_R[i] * SLICE_X[j], cy + r * STACK_Y[i], cz + r * STACK_R[i] * SLICE_Z[j],
						cx + r * STACK_R[i + 1] * SLICE_X[j], cy + r * STACK_Y[i + 1], cz + r * STACK_R[i + 1] * SLICE_Z[j],
						faint, faint, GRID_WIDTH);
			}
		}
	}

	/** Der Kreis, in dem die Kugel die Höhe des Spielers schneidet - nur in der Nähe, wie bei Beacons. */
	private static void heightCircle(VertexConsumer v, PoseStack.Pose pose, ConduitArea conduit, double y,
			double x, double z, Vec3 camera) {
		double dy = y - conduit.centerY();
		double r = conduit.range();
		if (Math.abs(dy) >= r) {
			return;
		}
		double radius = Math.sqrt(r * r - dy * dy);
		double horizontal = Math.sqrt(Mth.square(x - conduit.centerX()) + Mth.square(z - conduit.centerZ()));
		if (horizontal > radius + HEIGHT_LINE_REACH) {
			return;
		}
		circle(v, pose, (float) (conduit.centerX() - camera.x), (float) (y - camera.y), (float) (conduit.centerZ() - camera.z),
				(float) radius, argb(conduit.color(), 1.0F), HEIGHT_LINE_WIDTH);
	}

	private static void circle(VertexConsumer v, PoseStack.Pose pose, float cx, float y, float cz, float radius,
			int color, float width) {
		for (int j = 0; j < SPHERE_SLICES; j++) {
			line(v, pose, cx + radius * SLICE_X[j], y, cz + radius * SLICE_Z[j],
					cx + radius * SLICE_X[j + 1], y, cz + radius * SLICE_Z[j + 1], color, color, width);
		}
	}

	/** Die zwölf Kanten eines Quaders. */
	private static void cube(VertexConsumer v, PoseStack.Pose pose, AABB box, Vec3 camera, int color, float width) {
		ring(v, pose, box, box.minY, camera, color, width);
		ring(v, pose, box, box.maxY, camera, color, width);
		float y1 = (float) (box.minY - camera.y), y2 = (float) (box.maxY - camera.y);
		double[][] corners = {{box.minX, box.minZ}, {box.maxX, box.minZ}, {box.maxX, box.maxZ}, {box.minX, box.maxZ}};
		for (double[] c : corners) {
			float x = (float) (c[0] - camera.x), z = (float) (c[1] - camera.z);
			line(v, pose, x, y1, z, x, y2, z, color, color, width);
		}
	}

	// ------------------------------------------------------------------ Beacon: Kasten

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
		// Die Kamera liegt im Ursprung: auf der Innenseite einer Wand, wenn sie zwischen Wand und Gegenwand steht.
		wall(v, m, x1, z1, x2, z1, y1, y2, z1 < 0, bottom, top, outsideBottom, outsideTop); // Norden
		wall(v, m, x2, z1, x2, z2, y1, y2, x2 > 0, bottom, top, outsideBottom, outsideTop); // Osten
		wall(v, m, x2, z2, x1, z2, y1, y2, z2 > 0, bottom, top, outsideBottom, outsideTop); // Süden
		wall(v, m, x1, z2, x1, z1, y1, y2, x1 < 0, bottom, top, outsideBottom, outsideTop); // Westen
	}

	/**
	 * Eine senkrechte Fläche, nur von der Seite, auf der die Kamera ist: von innen deutlich, von außen schwach. So
	 * stimmt es, egal ob Minecraft Rückseiten weglässt (1.21.11) oder beide Seiten zeichnet (26.x). Die Ecken laufen im
	 * Uhrzeigersinn um den Bereich (von oben gesehen), dadurch zeigt die erste Reihenfolge nach innen.
	 */
	private static void wall(VertexConsumer v, Matrix4f m, float ax, float az, float bx, float bz,
			float y1, float y2, boolean inside, int bottom, int top, int outsideBottom, int outsideTop) {
		if (inside) {
			v.addVertex(m, ax, y1, az).setColor(bottom);
			v.addVertex(m, bx, y1, bz).setColor(bottom);
			v.addVertex(m, bx, y2, bz).setColor(top);
			v.addVertex(m, ax, y2, az).setColor(top);
		} else {
			v.addVertex(m, ax, y2, az).setColor(outsideTop);
			v.addVertex(m, bx, y2, bz).setColor(outsideTop);
			v.addVertex(m, bx, y1, bz).setColor(outsideBottom);
			v.addVertex(m, ax, y1, az).setColor(outsideBottom);
		}
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
