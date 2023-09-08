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

package org.quiltmc.loader.impl.plugin.quilt;

import java.io.IOException;
import java.nio.file.Path;

import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.solver.QuiltFileHasher;
import org.quiltmc.loader.impl.metadata.qmj.InternalModMetadata;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** A specialised {@link BuiltinModOption} which never has a useful origin hash, usually because it's either unknown or
 * useless to base the transformer hash key off.
 * <p>
 * Currently only the 'java' mod uses this, however architecture, operating system, or hardware related builtin mods
 * would use this too. */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class SystemModOption extends BuiltinModOption {

	public SystemModOption(QuiltPluginContext pluginContext, InternalModMetadata meta, Path from, Path resourceRoot) {
		super(pluginContext, meta, from, resourceRoot);
	}

	@Override
	protected String nameOfType() {
		return "system";
	}

	@Override
	public byte[] computeOriginHash(QuiltFileHasher hasher) throws IOException {
		byte[] hash = new byte[hasher.getHashLength()];
		for (int i = 0; i < hash.length; i++) {
			hash[i] = (byte) i;
		}
		return hash;
	}

	@Override
	public boolean needsTransforming() {
		return false;
	}
}
