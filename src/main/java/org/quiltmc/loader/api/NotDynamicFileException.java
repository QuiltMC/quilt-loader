package org.quiltmc.loader.api;

import java.nio.file.FileSystemException;
import java.nio.file.NotLinkException;

/** Thrown by {@link ExtendedFileSystem#readDynamicFileSource(java.nio.file.Path)} if the file was not a dynamic file.
 * This is similar in spirit to {@link NotLinkException} */
public class NotDynamicFileException extends FileSystemException {

	public NotDynamicFileException(String file) {
		super(file);
	}

	public NotDynamicFileException(String file, String other, String reason) {
		super(file, other, reason);
	}
}
