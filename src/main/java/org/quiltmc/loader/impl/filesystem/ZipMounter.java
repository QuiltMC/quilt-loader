/*
 * Copyright 2025 QuiltMC
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

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.jar.Manifest;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;

import org.jetbrains.annotations.NotNull;
import org.quiltmc.loader.impl.filesystem.QuiltUnifiedEntry.QuiltUnifiedFolder;
import org.quiltmc.loader.impl.filesystem.QuiltUnifiedEntry.QuiltUnifiedFolderWriteable;
import org.quiltmc.loader.impl.filesystem.QuiltUnifiedEntry.QuiltUnifiedMountedFile;
import org.quiltmc.loader.impl.filesystem.QuiltZipFileSystem.CountingInputStream;
import org.quiltmc.loader.impl.filesystem.QuiltZipFileSystem.CustomZipInputStream;
import org.quiltmc.loader.impl.util.HashUtil;
import org.quiltmc.loader.impl.util.JavaVersionUtil;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
class ZipMounter <@NotNull FS extends QuiltMapFileSystem<FS, P>, @NotNull P extends QuiltMapPath<FS, P>> {

	private final FS fs;
	private final P dst;
	private final ZipSource source;
	private final ZipMountType mountType;

	private ZipMounter(Path src, P dst, ZipMountType mountType) throws IOException {
		this.fs = dst.fs;
		this.dst = dst;
		this.mountType = mountType;
		if (src.getFileSystem() == FileSystems.getDefault()) {
			source = new ZipSource.SharedByteChannels(src);
		} else {
			source = new ZipSource.InputStreamSource(Files.newInputStream(src));
		}
	}

	static <@NotNull FS extends QuiltMapFileSystem<FS, P>, @NotNull P extends QuiltMapPath<FS, P>>
	void mountZipAt(Path zip, P dst, String zipPathPrefix, ZipHandling type) throws IOException {

		ZipMountType mountType = dst.fs.isReadOnly() ? ZipMountType.READ_ONLY : ZipMountType.COPY_ON_WRITE;

		new ZipMounter<>(zip, dst, mountType).mount(zipPathPrefix, type);
	}

	private void mount(String zipPathPrefix, ZipHandling type) throws IOException {
		dst.fs.addSource(source);

		// Check for our header
		byte[] header = new byte[QuiltZipCustomCompressedWriter.HEADER.length];
		try (InputStream fileStream = source.openConstructingStream()) {
			BufferedInputStream pushback = new BufferedInputStream(fileStream);
			pushback.mark(header.length);
			int readLength = pushback.read(header);
			if (readLength == 0 || readLength == -1) {
				throw new ZeroByteFileException("Zip start header not found - 0 byte file!");
			}
			if (readLength == header.length && Arrays.equals(header, QuiltZipCustomCompressedWriter.HEADER)) {
				if (!(source instanceof ZipSource.SharedByteChannels)) {
					throw new IOException("Cannot read a custom compressed stream that isn't on the default file system!");
				}
				int directoryStart = new DataInputStream(pushback).readInt();
				try (GZIPInputStream src = new GZIPInputStream(source.stream(directoryStart, -1))) {
					readDirectory(dst, new DataInputStream(src), zipPathPrefix);
				}
			} else if (readLength == header.length && Arrays.equals(header, QuiltZipCustomCompressedWriter.PARTIAL_HEADER)) {
				throw new PartiallyWrittenIOException();
			} else if (readLength <= 3) {
				throw new IOException("File is too small to contain a ZIP header! (" + readLength + " bytes)");
			} else if (header[0] != 0x50 || header[1] != 0x4b || header[2] != 0x03 || header[3] != 0x04) {
				String actuallyRead = HashUtil.hashToString(header, 0, readLength);
				throw new IOException("File header doesn't match the ZIP magic number! " + actuallyRead);
			} else {
				pushback.reset();
				initializeFromZip(pushback, zipPathPrefix);
			}
		}

		source.build();

		if (type == ZipHandling.JAR) {
			setupMultiReleaseJar();
		}
	}

	private void setupMultiReleaseJar() throws IOException {

		int javaVersion = JavaVersionUtil.getJavaVersion();
		if (javaVersion < 9) {
			return;
		}

		P metaInf = dst.resolve("META-INF");
		if (!fs.exists(metaInf)) {
			return;
		}
		P versionsPath = metaInf.resolve("versions");
		if (!fs.exists(versionsPath)) {
			return;
		}
		P manifestPath = metaInf.resolve("MANIFEST.MF");
		if (!fs.exists(manifestPath)) {
			return;
		}

		try (InputStream manifestStream = Files.newInputStream(manifestPath)) {
			Manifest manifest = new Manifest(manifestStream);
			String multiReleaseValue = manifest.getMainAttributes().getValue("Multi-Release");
			if (!"true".equalsIgnoreCase(multiReleaseValue)) {
				return;
			}
		}

		for (int version = 9; version <= javaVersion; version++) {
			P exactVersionPath = versionsPath.resolve(Integer.toString(version));
			if (!fs.exists(exactVersionPath)) {
				continue;
			}

			if (!fs.isDirectory(exactVersionPath)) {
				continue;
			}

			linkMultiReleaseEntry(exactVersionPath, dst);
		}
	}

	private void linkMultiReleaseEntry(Path from, P to) throws IOException {
		QuiltUnifiedEntry entry = fs.getEntry(from);
		if (entry instanceof QuiltUnifiedFolder) {
			QuiltUnifiedFolder folder = (QuiltUnifiedFolder) entry;
			String folderName = folder.path.name;
			if ("META-INF".equals(folderName)) {
				return;
			}

			if (!fs.isDirectory(to)) {
				fs.addEntryRequiringParent(new QuiltUnifiedFolderWriteable(to));
			}

			for (Path child : folder.getChildren()) {
				linkMultiReleaseEntry(child, to.resolve(child.getFileName()));
			}
		} else {
			fs.removeEntry(to, false);
			fs.addEntryRequiringParent(new QuiltUnifiedMountedFile(to, from, true));
		}
	}

	private void initializeFromZip(InputStream fileStream, String zipPathPrefix) throws IOException {
		try (CountingInputStream counter = new CountingInputStream(fileStream); //
			CustomZipInputStream zip = new CustomZipInputStream(counter)//
		) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				String entryName = entry.getName();

				if (!entryName.startsWith(zipPathPrefix)) {
					continue;
				}
				entryName = entryName.substring(zipPathPrefix.length());
				if (!entryName.startsWith("/")) {
					entryName = "/" + entryName;
				}

				P path = fs.getPath(entryName);

				if (entryName.endsWith("/")) {
					fs.createDirectories(path);
				} else if (fs.exists(path)) {
					throw new IOException("Duplicate entry " + path);
				} else {
					fs.addEntryAndParents(mountType.create(path, source, entry, zip));
				}
			}
		}
	}

	private void readDirectory(P path, DataInputStream stream, String zipPathPrefix) throws IOException {
		String pathString = path.toString();
		if (pathString.startsWith(zipPathPrefix) || zipPathPrefix.startsWith(pathString)) {
			fs.createDirectories(path);
		}
		int childFiles = stream.readUnsignedShort();
		for (int i = 0; i < childFiles; i++) {
			int length = stream.readUnsignedByte();
			byte[] nameBytes = new byte[length];
			stream.readFully(nameBytes);
			P filePath = path.resolve(new String(nameBytes, StandardCharsets.UTF_8));
			int offset = stream.readInt();
			int uncompressedSize = stream.readInt();
			int compressedSize = stream.readInt();
			if (filePath.toString().startsWith(zipPathPrefix)) {
				fs.addEntryAndParents(mountType.create(filePath, source, offset, compressedSize, uncompressedSize, true));
			}
		}

		int childFolders = stream.readUnsignedShort();
		for (int i = 0; i < childFolders; i++) {
			int length = stream.readUnsignedByte();
			byte[] nameBytes = new byte[length];
			stream.readFully(nameBytes);
			String name = new String(nameBytes, StandardCharsets.UTF_8);
			readDirectory(path.resolve(name), stream, zipPathPrefix);
		}
	}
}
