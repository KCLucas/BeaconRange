package com.kclucas.beaconrange.client.mixin;

import com.kclucas.beaconrange.client.BeaconRangeClient;
import com.kclucas.beaconrange.client.RangeRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Zeichnet die Bereiche an denselben Stellen wie 1.21.11 mit WorldRenderEvents.BEFORE_TRANSLUCENT: die Wände vor dem
 * durchsichtigen Gelände (Wasser, Glas), die Linien danach - dort zeichnet 1.21.11 seinen offenen Linien-Stapel erst
 * am Ende des Durchgangs. Fabric API für 1.21.4 hat kein BEFORE_TRANSLUCENT, deshalb direkt im Hauptdurchgang.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

	@Shadow
	@Final
	private RenderBuffers renderBuffers;

	/** Der Hauptdurchgang der Welt (Lambda in addMainPass): kurz bevor das durchsichtige Gelände gezeichnet wird. */
	@Inject(method = "method_62214", at = @At(value = "INVOKE_STRING",
			target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", args = "ldc=translucent"))
	private void beaconrange$beforeTranslucent(CallbackInfo ci) {
		// Wie bei Vanilla an dieser Stelle: ein frischer PoseStack, die Kamera steckt schon in der Ansichtsmatrix.
		RangeRenderer.renderWalls(new PoseStack(), this.renderBuffers.bufferSource(), beaconrange$camera(),
				BeaconRangeClient.getVisible(), BeaconRangeClient.getVisibleConduits());
	}

	/** Gleich nach dem durchsichtigen Gelände (vor Stolperdraht und Blockumriss). */
	@Inject(method = "method_62214", at = @At(value = "INVOKE_STRING",
			target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", args = "ldc=string"))
	private void beaconrange$afterTranslucent(CallbackInfo ci) {
		RangeRenderer.renderLines(new PoseStack(), this.renderBuffers.bufferSource(), beaconrange$camera(),
				BeaconRangeClient.getVisible(), BeaconRangeClient.getVisibleConduits());
	}

	/** Dieselbe Kamera, die der Hauptdurchgang bekommt. */
	private static Vec3 beaconrange$camera() {
		return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
	}
}
