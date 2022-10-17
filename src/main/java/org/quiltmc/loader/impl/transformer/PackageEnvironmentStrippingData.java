package org.quiltmc.loader.impl.transformer;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Type;
import org.quiltmc.loader.api.minecraft.ClientOnly;
import org.quiltmc.loader.api.minecraft.DedicatedServerOnly;

import net.fabricmc.api.EnvType;

public class PackageEnvironmentStrippingData extends ClassVisitor {

	private static final String CLIENT_ONLY_DESCRIPTOR = Type.getDescriptor(ClientOnly.class);
	private static final String SERVER_ONLY_DESCRIPTOR = Type.getDescriptor(DedicatedServerOnly.class);

	private final EnvType envType;
	public boolean stripEntirePackage = false;

	public PackageEnvironmentStrippingData(int api, EnvType envType) {
		super(api);
		this.envType = envType;
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		if (CLIENT_ONLY_DESCRIPTOR.equals(descriptor)) {
			if (envType == EnvType.SERVER) {
				stripEntirePackage = true;
			}
		} else if (SERVER_ONLY_DESCRIPTOR.equals(descriptor)) {
			if (envType == EnvType.CLIENT) {
				stripEntirePackage = true;
			}
		}
		return null;
	}
}
