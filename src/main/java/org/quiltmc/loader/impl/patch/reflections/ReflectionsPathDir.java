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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** Patch class that is transformed by {@link ReflectionsClassPatcher} to implement "org.reflections.vfs.Vfs.Dir"*/
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
class ReflectionsPathDir {
    final Path path;

    public ReflectionsPathDir(Path path) {
        this.path = path;
    }

    public String getPath() {
        return path.toString().replace(path.getFileSystem().getSeparator(), "/");
    }

    public Iterable<Object> getFiles() {
        if (!FasterFiles.isDirectory(path)) {
            return Collections.emptyList();
        }
        return () -> {
            try {
                return Files.walk(path)
                        .filter(Files::isRegularFile)
                        .map(p -> (Object) new ReflectionsPathFile(ReflectionsPathDir.this, p))
                        .iterator();
            } catch (IOException e) {
                throw new RuntimeException("could not get files for " + path, e);
            }
        };
    }
}
