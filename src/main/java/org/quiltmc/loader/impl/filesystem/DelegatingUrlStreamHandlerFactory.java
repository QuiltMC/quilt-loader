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

import java.net.URL;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.SystemProperties;

/** Holds the {@link URLStreamHandlerFactory} for all quilt filesystems. This is set to
 * {@link URL#setURLStreamHandlerFactory(URLStreamHandlerFactory)}.
 * <p>
 * Game providers for games other than minecraft are expected to append their games' factories to
 * {@link #appendFactory(URLStreamHandlerFactory)}, if their game actually needs this. */
@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public class DelegatingUrlStreamHandlerFactory implements URLStreamHandlerFactory {

	public static final DelegatingUrlStreamHandlerFactory INSTANCE = new DelegatingUrlStreamHandlerFactory();
	private static final boolean DISABLED;

	private URLStreamHandlerFactory[] factories = new URLStreamHandlerFactory[0];

	static {
		DISABLED = Boolean.getBoolean(SystemProperties.DISABLE_URL_STREAM_FACTORY);
		if (!DISABLED) {
			URL.setURLStreamHandlerFactory(INSTANCE);
		}
	}

	static void load() {
		// Just calls <clinit>
	}

	public static synchronized void appendFactory(URLStreamHandlerFactory factory) {
		if (DISABLED) {
			throw new Error(
				"The system property '" + SystemProperties.DISABLE_URL_STREAM_FACTORY
					+ "' has been set to true, which disables custom factories - you will need to reconfigure your environment!"
			);
		}
		URLStreamHandlerFactory[] copy = new URLStreamHandlerFactory[INSTANCE.factories.length + 1];
		System.arraycopy(INSTANCE.factories, 0, copy, 0, INSTANCE.factories.length);
		copy[INSTANCE.factories.length] = factory;
		INSTANCE.factories = copy;
	}

	@Override
	public URLStreamHandler createURLStreamHandler(String protocol) {
		if (QuiltMemoryFileSystemProvider.SCHEME.equals(protocol)) {
			return new org.quiltmc.loader.impl.filesystem.quilt.mfs.Handler();
		}

		if (QuiltJoinedFileSystemProvider.SCHEME.equals(protocol)) {
			return new org.quiltmc.loader.impl.filesystem.quilt.jfs.Handler();
		}

		if (QuiltZipFileSystemProvider.SCHEME.equals(protocol)) {
			return new org.quiltmc.loader.impl.filesystem.quilt.zfs.Handler();
		}

		URLStreamHandlerFactory[] array = factories;
		for (URLStreamHandlerFactory factory : array) {
			URLStreamHandler handler = factory.createURLStreamHandler(protocol);
			if (handler != null) {
				return handler;
			}
		}

		return null;
	}
}
