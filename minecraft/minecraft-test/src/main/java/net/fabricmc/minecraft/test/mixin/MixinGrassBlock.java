package net.fabricmc.minecraft.test.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Fertilizable;
import net.minecraft.block.GrassBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.random.RandomGenerator;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GrassBlock.class)
public abstract class MixinGrassBlock implements Fertilizable {
	@Override
	@Overwrite
	public boolean canFertilize(World world, RandomGenerator random, BlockPos pos, BlockState state) {
		return false;
	}

    @WrapOperation(method = "isFertilizable", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldView;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;"))
    private BlockState testMixinExtras(WorldView instance, BlockPos pos, Operation<BlockState> original, @Local(argsOnly = true) BlockState state) {
        if (state == null) {
            return Blocks.AIR.getDefaultState();
        }

        return original.call(instance, pos);
    }
}
