/*
 * Copyright 2016 FabricMC
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

package org.quiltmc.loader.impl.solver;

import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.discovery.ModCandidate;
import org.quiltmc.loader.impl.discovery.ModResolver;
import org.quiltmc.loader.impl.metadata.qmj.FabricModMetadataWrapper;

abstract class ModLoadOption extends LoadOption {
	final ModCandidate candidate;

	ModLoadOption(ModCandidate candidate) {
		this.candidate = candidate;
	}

	String getSourceIcon() {
		if (FabricModMetadataWrapper.GROUP.equals(candidate.getMetadata().group())) {
			return "$jar+fabric$";
		} else {
			return "$jar+quilt$";
		}
	}
	
	String group() {
		return candidate.getMetadata().group();
	}

	String modId() {
		return candidate.getMetadata().id();
	}

	Version version() {
		return candidate.getMetadata().version();
	}

	@Override
	public String toString() {
		return shortString();
	}
	
	abstract String shortString();

	String fullString() {
		return shortString() + " " + getSpecificInfo();
	}

	String getLoadSource() {
		// TODO: getReadablePath?
		return candidate.getOriginPath().toString();
	}

	abstract String getSpecificInfo();

	abstract MainModLoadOption getRoot();
}
