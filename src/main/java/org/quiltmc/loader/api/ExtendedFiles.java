package org.quiltmc.loader.api;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;

/** Similar to {@link Files}, but for {@link ExtendedFileSystem}. Unlike {@link Files}, most operations can take
 * {@link Path}s from any file system. */
public class ExtendedFiles {

	/** Copies the source file to the target file. If the source file system is read-only then the target file may
	 * become a link to the source file, which is fully copied when it is modified.
	 * <p>
	 * This method is a safe alternative to {@link #mount(Path, Path, MountOption...)}, when passing them
	 * {@link MountOption#COPY_ON_WRITE}, in the sense that it will copy the file if the filesystem doesn't support
	 * mounts. */
	public static Path copyOnWrite(Path source, Path target, CopyOption... options) throws IOException {
		if (target.getFileSystem() instanceof ExtendedFileSystem) {
			return ((ExtendedFileSystem) target.getFileSystem()).copyOnWrite(source, target, options);
		} else {
			return Files.copy(source, target, options);
		}
	}

	/** Attempts to mount the source file onto the target file, such that all reads and writes to the target file
	 * actually read and write the source file. (The exact behaviour depends on the options given).
	 * <p>
	 * This is similar to {@link Files#createSymbolicLink(Path, Path, java.nio.file.attribute.FileAttribute...)}, but
	 * the source file and target file don't need to be on the same filesystem.
	 * <p>
	 * This does not support mounting folders.
	 * 
	 * @throws UnsupportedOperationException if the filesystem doesn't support this operation.
	 * @throws IOException if anything goes wrong while mounting the file. */
	public static Path mount(Path source, Path target, MountOption... options) throws IOException {
		if (target.getFileSystem() instanceof ExtendedFileSystem) {
			return ((ExtendedFileSystem) target.getFileSystem()).mount(source, target, options);
		} else {
			throw new UnsupportedOperationException(target.getFileSystem() + " does not support file mounts!");
		}
	}
}
