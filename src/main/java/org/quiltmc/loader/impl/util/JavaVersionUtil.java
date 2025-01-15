/*
 * Copyright 2024 QuiltMC
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

package org.quiltmc.loader.impl.util;

@MultiReleaseJarCandidate
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public final class JavaVersionUtil {

	private static int JAVA_VERSION = -1;

	public static int getJavaVersion() {
		if (JAVA_VERSION < 0) {
			String jVersion = System.getProperty("java.version", "");
			if (jVersion.startsWith("1.")) {
				// Java 8 or earlier
				// However loader itself requires java 8, so just force java 8
				JAVA_VERSION = 8;
			} else {
				int firstDot = jVersion.indexOf('.');
				if (firstDot > 0) {
					try {
						JAVA_VERSION = Integer.parseInt(jVersion.substring(0, firstDot));
					} catch (NumberFormatException nfe) {
						throw new IllegalStateException(
							"Unable to convert 'java.version' (" + jVersion + ") into a version number!", nfe
						);
					}
				} else {
					throw new IllegalStateException("Unable to convert 'java.version' (" + jVersion + ") into a version number!");
				}
			}
		}

		return JAVA_VERSION;
	}
}
