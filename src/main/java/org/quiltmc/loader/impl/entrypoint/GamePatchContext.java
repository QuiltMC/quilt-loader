package org.quiltmc.loader.impl.entrypoint;

import org.jetbrains.annotations.ApiStatus;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@ApiStatus.NonExtendable
@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public interface GamePatchContext {

	/** @return A {@link ClassReader} which reads the original class file. */
	ClassReader getClassSourceReader(String className);

	/** @return A {@link ClassNode}, which may have already been modified by another {@link GamePatch}. */
	ClassNode getClassNode(String className);

	void addPatchedClass(ClassNode patchedClass);
}
