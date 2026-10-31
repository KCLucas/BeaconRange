package com.kclucas.beaconrange.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lage des Beacon-Fensters, damit der Knopf daneben sitzt - auch wenn eine andere Mod es verschiebt. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

	@Accessor("leftPos")
	int beaconrange$getLeftPos();

	@Accessor("topPos")
	int beaconrange$getTopPos();

	@Accessor("imageWidth")
	int beaconrange$getImageWidth();
}
