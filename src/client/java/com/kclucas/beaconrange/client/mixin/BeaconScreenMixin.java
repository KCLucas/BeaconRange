package com.kclucas.beaconrange.client.mixin;

import com.kclucas.beaconrange.client.BeaconRangeClient;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.BeaconScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeaconScreen.class)
public class BeaconScreenMixin {

    @Unique
    private BlockPos targetedPos;

    @Inject(at = @At("TAIL"), method = "init")
    private void addToggleButton(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (targetedPos == null) {
            HitResult hit = client.crosshairTarget;
            if (hit instanceof BlockHitResult blockHit) {
                BlockPos pos = blockHit.getBlockPos();
                if (client.world != null && client.world.getBlockEntity(pos) instanceof BeaconBlockEntity) {
                    targetedPos = pos;
                }
            }
        }

        if (targetedPos == null) return;

        BeaconScreen screen = (BeaconScreen)(Object)this;
        int sx = ((HandledScreenAccessor) screen).getX();
        int sy = ((HandledScreenAccessor) screen).getY();
        int bw = ((HandledScreenAccessor) screen).getBackgroundWidth();

        ButtonWidget button = ButtonWidget.builder(
                getButtonText(targetedPos),
                btn -> {
                    BeaconRangeClient.togglePin(targetedPos);
                    btn.setMessage(getButtonText(targetedPos));
                }
        ).dimensions(sx + bw - 95, sy - 25, 90, 20).build();

        ((ScreenAccessor) screen).invokeAddDrawableChild(button);
    }

    @Unique
    private Text getButtonText(BlockPos pos) {
        // Check if THIS specific beacon is in the set
        boolean isPinned = BeaconRangeClient.pinnedBeacons.contains(pos);
        return Text.literal(isPinned ? "§aRange: ON" : "§cRange: OFF");
    }
}