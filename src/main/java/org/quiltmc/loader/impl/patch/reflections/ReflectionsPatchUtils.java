package org.quiltmc.loader.impl.patch.reflections;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** Used for various method implementations of {@link ReflectionsClassPatcher} */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class ReflectionsPatchUtils {

	// TODO: Use these!

	public static String dir_getPath(Path path) {
		return path.toString().replace(path.getFileSystem().getSeparator(), "/");
	}

	public static Iterable<Object> dir_getFiles(ReflectionsDir dir, Path in) {
		if (!FasterFiles.isDirectory(in)) {
			return Collections.emptyList();
		}
		return () -> {
			try {
				return Files.walk(in).filter(Files::isRegularFile).map(dir::createFile).iterator();
			} catch (IOException e) {
				throw new RuntimeException("Could not get files for " + in, e);
			}
		};
	}

	public static String file_getRelativePath(Path root, Path path) {
		return root.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/");
	}

    public static InputStream file_openInputStream(Path path) throws IOException {
        return Files.newInputStream(path);
    }
}
