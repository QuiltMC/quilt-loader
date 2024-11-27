package net.fabricmc.minecraft.test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.MappingResolver;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.mappingio.tree.MappingTreeView;
import net.fabricmc.mappingio.tree.MappingTreeView.MethodMappingView;

import net.minecraft.entity.Entity;
import net.minecraft.item.CompassItem;
import net.minecraft.item.DyeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

public class FabricEntrypointTest implements ModInitializer {
	@Override
	public void onInitialize() {
		System.out.println("testmod initialized!");

		System.out.println("Testing ReflectiveFixer");
		try {
			String clnom = ServerPlayerEntity.class.getName() + "$2"; // An anonymous inner class
			Class<?> cls = Class.forName(clnom, true, FabricEntrypointTest.class.getClassLoader());
			MappingResolver mappings = QuiltLoader.getMappingResolver();
			MappingTreeView mappingsView = QuiltLauncherBase.getLauncher().getMappingConfiguration().getMappings();
			String currentNamespace = mappings.getCurrentRuntimeNamespace();

			for (String namespace : mappings.getNamespaces()) {
				String name = mappings.unmapClassName(namespace, cls.getName());
				System.out.println(
					"[" + (currentNamespace.equals(namespace) ? "x" : " ") + "] " + namespace + " = " + name
				);
			}

			System.out.println(cls.getName());
			System.out.println(Arrays.toString(cls.getInterfaces()));
			System.out.println(cls.getDeclaredField("field_29183"));
			System.out.println(cls.getField("field_29183"));
			MethodHandle getter = MethodHandles.privateLookupIn(cls, MethodHandles.lookup()).findGetter(
				cls, "field_29183", ServerPlayerEntity.class
			);
			int h = 0;
			for (int i = 0; i < 100_000; i++) {
				MethodHandle handle = MethodHandles.privateLookupIn(cls, MethodHandles.lookup()).findGetter(
					cls, "field_29183", ServerPlayerEntity.class
				);
				h = h + 1 * handle.hashCode();
			}
			System.out.println(h);
			System.out.println("Obtained getter " + getter + " as " + getter.type().toMethodDescriptorString());

			// inventoryTick (ItemStack stack, World world, Entity entity, int slot, boolean selected)V
			MethodType type = MethodType.methodType(void.class, ItemStack.class, World.class, Entity.class, int.class, boolean.class);
			String intermediaryName = null;
			for (Method m : Item.class.getDeclaredMethods()) {
				if (m.getReturnType() != type.returnType() || !Arrays.equals(type.parameterArray(), m.getParameterTypes())) {
					continue;
				}
				System.out.println(m.getName());
				// Should we publish this as API?
				int i = mappingsView.getNamespaceId("named");
				MethodMappingView method = mappingsView.getMethod(Item.class.getName().replace('.', '/'), "inventoryTick", type.descriptorString(), i);
				if (method != null) {
					System.out.println(method.getName("official"));
					System.out.println(intermediaryName = method.getName("intermediary"));
					System.out.println(method.getName("named"));
					break;
				}
			}
			System.out.println("Method Name = " + intermediaryName);
			System.out.println("Method (Virtual) = " + MethodHandles.lookup().findVirtual(CompassItem.class, intermediaryName, type));
			Lookup privateInItem = MethodHandles.privateLookupIn(Item.class, MethodHandles.lookup());
			System.out.println("Method (Special) = " + privateInItem.findSpecial(Item.class, intermediaryName, type, Item.class));

			System.out.println("Method (Declared) = " + Item.class.getDeclaredMethod(intermediaryName, type.parameterArray()));
			System.out.println("Method (Any) = " + CompassItem.class.getMethod(intermediaryName, type.parameterArray()));

			int namespace = mappingsView.getNamespaceId("named");
			MethodMappingView method = mappingsView.getMethod(CompassItem.class.getName().replace('.', '/'), "getSpawnPosition", null, namespace);
			System.out.println("getSpawnPosition = " + method);

		} catch (ReflectiveOperationException e) {
			throw new Error(e);
		}
	}
}
