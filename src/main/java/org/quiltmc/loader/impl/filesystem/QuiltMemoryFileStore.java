/*
 * Copyright 2022, 2023 QuiltMC
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
import java.nio.file.FileStore;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;

import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public abstract class QuiltMemoryFileStore extends FileStore {

	final String name;

	private QuiltMemoryFileStore(String name) {
		this.name = name;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public String type() {
		return "quilt-in-memory";
	}

	@Override
	public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
		return type == BasicFileAttributeView.class;
	}

	@Override
	public boolean supportsFileAttributeView(String name) {
		return "basic".equals(name);
	}

	@Override
	public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
		return null;
	}

	@Override
	public Object getAttribute(String attribute) throws IOException {
		return null;
	}

	public static final class ReadOnly extends QuiltMemoryFileStore {
		private final int totalSize;

		ReadOnly(String name, int totalSize) {
			super(name);
			this.totalSize = totalSize;
		}

		@Override
		public boolean isReadOnly() {
			return true;
		}

		@Override
		public long getTotalSpace() throws IOException {
			return totalSize;
		}

		@Override
		public long getUsableSpace() throws IOException {
			return totalSize;
		}

		@Override
		public long getUnallocatedSpace() throws IOException {
			return 0;
		}
	}

	public static final class ReadWrite extends QuiltMemoryFileStore {
		private final QuiltMemoryFileSystem.ReadWrite fs;

		public ReadWrite(String name, QuiltMemoryFileSystem.ReadWrite fs) {
			super(name);
			this.fs = fs;
		}

		@Override
		public boolean isReadOnly() {
			return false;
		}

		@Override
		public long getTotalSpace() throws IOException {
			// TODO Auto-generated method stub
			throw new AbstractMethodError("// TODO: Implement this!");
		}

		@Override
		public long getUsableSpace() throws IOException {
			// TODO Auto-generated method stub
			throw new AbstractMethodError("// TODO: Implement this!");
		}

		@Override
		public long getUnallocatedSpace() throws IOException {
			// TODO Auto-generated method stub
			throw new AbstractMethodError("// TODO: Implement this!");
		}
	}
}
