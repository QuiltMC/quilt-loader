package org.quiltmc.loader.impl.patch.reflections;

import java.nio.file.Path;

public interface ReflectionsDir {
	Object createFile(Path in);
}
