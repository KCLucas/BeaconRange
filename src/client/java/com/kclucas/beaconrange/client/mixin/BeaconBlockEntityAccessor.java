package com.kclucas.beaconrange.client.mixin;

import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Die Stufe (1-4), die der Beacon selbst ausgerechnet hat. */
@Mixin(BeaconBlockEntity.class)
public interface BeaconBlockEntityAccessor {

	@Accessor("levels")
	int beaconrange$getLevels();
}
