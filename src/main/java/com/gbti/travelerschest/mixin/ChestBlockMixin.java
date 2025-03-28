package com.gbti.travelerschest.mixin;

import com.gbti.travelerschest.TravelersChest;
import com.gbti.travelerschest.utils.ChestObject;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.gbti.travelerschest.TravelersChest.chests;

@Mixin(ChestBlock.class)
public class ChestBlockMixin {
    @Inject(method = "onUse", at = @At("HEAD"))
    private void onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        if (world.getBlockEntity(pos) instanceof ChestBlockEntity && chests.containsKey(pos.asLong())) {
            TravelersChest.checkAndRefillChest(world, pos, pos.asLong());
        }
    }
}
