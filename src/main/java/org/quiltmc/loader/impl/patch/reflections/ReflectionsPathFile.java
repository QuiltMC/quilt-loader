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

package org.quiltmc.loader.impl.patch.reflections;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** Patch class that is transformed by {@link ReflectionsClassPatcher} to implement "org.reflections.vfs.Vfs.File" */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
class ReflectionsPathFile {

    private final ReflectionsPathDir root;
    private final Path path;

    public ReflectionsPathFile(ReflectionsPathDir root, Path path) {
        this.root = root;
        this.path = path;
    }

    public String getName() {
        return path.getFileName().toString();
    }

    public String getRelativePath() {
        return root.path.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/");
    }

    public InputStream openInputStream() throws IOException {
        return Files.newInputStream(path);
    }

    @Override
	public String toString() {
        return path.toString();
    }
}
