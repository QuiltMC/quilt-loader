/*
 * Copyright 2023 QuiltMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.quiltmc.loader.impl.filesystem;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public abstract /* sealed */ class QuiltUnifiedEntry /* permits QuiltUnifiedFolder, QuiltUnifiedFile */ {

	// We don't actually need generics at this point
	final QuiltMapPath<?, ?> path;

	private QuiltUnifiedEntry(QuiltMapPath<?, ?> path) {
		this.path = path.toAbsolutePath().normalize();
	}

	@Override
	public String toString() {
		return path + " " + getClass().getName();
	}

	protected abstract BasicFileAttributes createAttributes() throws IOException;

	protected QuiltUnifiedEntry switchToReadOnly() {
		return this;
	}

	/** @return A new entry which has been copied to the new path. Might not be on the same filesystem. */
	protected abstract QuiltUnifiedEntry createCopiedTo(QuiltMapPath<?, ?> newPath);

	/** Like {@link #createCopiedTo(QuiltMapPath)}, but used when the original file will be deleted - which allows some entries to
	 * be shallow copied. */
	protected QuiltUnifiedEntry createMovedTo(QuiltMapPath<?, ?> newPath) {
		return createCopiedTo(newPath);
	}

	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	public static abstract class QuiltUnifiedFolder extends QuiltUnifiedEntry {
		private QuiltUnifiedFolder(QuiltMapPath<?, ?> path) {
			super(path);
		}

		@Override
		protected BasicFileAttributes createAttributes() {
			return new QuiltFileAttributes(path, QuiltFileAttributes.SIZE_DIRECTORY);
		}

		protected abstract Collection<? extends Path> getChildren();
	}

	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	public static final class QuiltUnifiedFolderReadOnly extends QuiltUnifiedFolder {
		public final QuiltMapPath<?, ?>[] children;

		public QuiltUnifiedFolderReadOnly(QuiltMapPath<?, ?> path, QuiltMapPath<?, ?>[] children) {
			super(path);
			this.children = children;
		}

		@Override
		protected Collection<? extends Path> getChildren() {
			return Arrays.asList(children);
		}

		@Override
		protected QuiltUnifiedEntry createCopiedTo(QuiltMapPath<?, ?> newPath) {
			return new QuiltUnifiedFolderReadOnly(newPath, new QuiltMapPath[0]);
		}
	}

	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	public static final class QuiltUnifiedFolderWriteable extends QuiltUnifiedFolder {
		public final Set<QuiltMapPath<?, ?>> children = Collections.newSetFromMap(new ConcurrentHashMap<>());

		public QuiltUnifiedFolderWriteable(QuiltMapPath<?, ?> path) {
			super(path);
		}

		@Override
		protected Collection<? extends Path> getChildren() {
			return children;
		}

		@Override
		protected QuiltUnifiedEntry switchToReadOnly() {
			return new QuiltUnifiedFolderReadOnly(path, children.toArray(new QuiltMapPath[0]));
		}

		@Override
		protected QuiltUnifiedEntry createCopiedTo(QuiltMapPath<?, ?> newPath) {
			return new QuiltUnifiedFolderWriteable(newPath);
		}
	}

	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	public static abstract class QuiltUnifiedFile extends QuiltUnifiedEntry {
		public QuiltUnifiedFile(QuiltMapPath<?, ?> path) {
			super(path);
		}

		abstract InputStream createInputStream() throws IOException;

		abstract OutputStream createOutputStream(boolean append, boolean truncate) throws IOException;

		abstract SeekableByteChannel createByteChannel(Set<? extends OpenOption> options) throws IOException;
	}

	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	public static class QuiltUnifiedMountedFile extends QuiltUnifiedFile {

		public final Path to;
		public final boolean readOnly;

		public QuiltUnifiedMountedFile(QuiltMapPath<?, ?> path, Path to, boolean readOnly) {
			super(path);
			this.to = to;
			this.readOnly = readOnly;
		}

		@Override
		InputStream createInputStream() throws IOException {
			return Files.newInputStream(to);
		}

		@Override
		OutputStream createOutputStream(boolean append, boolean truncate) throws IOException {
			if (readOnly) {
				throw new IOException("ReadOnly");
			}
			List<OpenOption> options = new ArrayList<>(3);
			options.add(StandardOpenOption.WRITE);
			if (append) {
				options.add(StandardOpenOption.APPEND);
			}
			if (truncate) {
				options.add(StandardOpenOption.TRUNCATE_EXISTING);
			}
			return Files.newOutputStream(to, options.toArray(new OpenOption[0]));
		}

		@Override
		SeekableByteChannel createByteChannel(Set<? extends OpenOption> options) throws IOException {
			for (OpenOption option : options) {
				if (option != StandardOpenOption.READ && readOnly) {
					throw new IOException("ReadOnly");
				}
			}

			return Files.newByteChannel(to, options);
		}

		@Override
		protected BasicFileAttributes createAttributes() throws IOException {
			BasicFileAttributes attrs = Files.readAttributes(to, BasicFileAttributes.class);
			return new QuiltFileAttributes(this, attrs.size());
		}

		@Override
		protected QuiltUnifiedEntry switchToReadOnly() {
			if (readOnly) {
				return this;
			} else {
				return new QuiltUnifiedMountedFile(path, to, true);
			}
		}

		@Override
		protected QuiltUnifiedEntry createCopiedTo(QuiltMapPath<?, ?> newPath) {
			return new QuiltUnifiedMountedFile(newPath, to, readOnly);
		}
	}

	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	public static class QuiltUnifiedCopyOnWriteFile extends QuiltUnifiedMountedFile {
		public QuiltUnifiedCopyOnWriteFile(QuiltMapPath<?, ?> path, Path to) {
			super(path, to, false);
//			System.out.println("NEW copy-on-write " + path + "   ->   " + to);
		}

		@Override
		protected QuiltUnifiedEntry switchToReadOnly() {
			// If we're still present then we haven't been modified.
			return new QuiltUnifiedMountedFile(path, to, true);
		}

		@Override
		protected QuiltUnifiedEntry createCopiedTo(QuiltMapPath<?, ?> newPath) {
			return new QuiltUnifiedCopyOnWriteFile(newPath, to);
		}

		private QuiltUnifiedFile deepCopy(boolean truncate) throws IOException {
			System.out.println("REMOVED copy-on-write " + path);
			path.fs.provider().delete(path);
			QuiltMemoryFile.ReadWrite file = new QuiltMemoryFile.ReadWrite(path);
			if (!truncate) {
				try (OutputStream dst = file.createOutputStream(true, true)) {
					Files.copy(path, dst);
				}
			}
			path.fs.addEntryRequiringParent(file);
			return file;
		}

		@Override
		OutputStream createOutputStream(boolean append, boolean truncate) throws IOException {
			return deepCopy(truncate).createOutputStream(append, truncate);
		}

		@Override
		SeekableByteChannel createByteChannel(Set<? extends OpenOption> options) throws IOException {
			if (options.contains(StandardOpenOption.WRITE)) {
				boolean truncate = options.contains(StandardOpenOption.TRUNCATE_EXISTING);
				return deepCopy(truncate).createByteChannel(options);
			}
			return super.createByteChannel(options);
		}
	}
}
