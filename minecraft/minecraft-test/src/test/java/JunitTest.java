import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.quiltmc.loader.api.QuiltLoader;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.Blocks;
import net.minecraft.block.GrassBlock;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class JunitTest {
	@BeforeAll
	public static void setup() {
		System.out.println("Initializing Minecraft");
		MixinExtrasBootstrap.init();
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		System.out.println("Minecraft initialized");
	}

	@Test
	public void testItems() {
		Identifier id = Registries.ITEM.getId(Items.DIAMOND);
		assertEquals(id.toString(), "minecraft:diamond");

		System.out.println(id);
	}

	@Test
	public void testMixin() {
		// MixinGrassBlock sets canGrow to false
		GrassBlock grassBlock = (GrassBlock) Blocks.GRASS_BLOCK;
		boolean canGrow = grassBlock.canFertilize(null, null, null, null);
		assertFalse(canGrow);
	}

	@Test
	public void testMixinExtras() {
		// MixinGrassBlock sets isFertilizable to true
		GrassBlock grassBlock = (GrassBlock) Blocks.GRASS_BLOCK;
		System.out.println("Grass Block = " + grassBlock);
		boolean isFertilizable = grassBlock.isFertilizable(null, BlockPos.ORIGIN, null);
		assertTrue(isFertilizable);
	}

	@Test
	@SuppressWarnings("deprecation")
	public void testAccessLoader() {
		QuiltLoader.getAllMods();
		FabricLoader.getInstance().getAllMods();
	}
}
