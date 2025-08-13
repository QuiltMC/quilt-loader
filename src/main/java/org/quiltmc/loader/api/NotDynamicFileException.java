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
