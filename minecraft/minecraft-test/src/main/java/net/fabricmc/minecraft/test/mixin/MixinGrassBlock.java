package net.fabricmc.minecraft.test.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Fertilizable;
import net.minecraft.block.GrassBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.random.RandomGenerator;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(GrassBlock.class)
public abstract class MixinGrassBlock implements Fertilizable {
	@Override
	@Overwrite
	public boolean canGrow(World world, RandomGenerator random, BlockPos pos, BlockState state) {
		return false;
	}
}
