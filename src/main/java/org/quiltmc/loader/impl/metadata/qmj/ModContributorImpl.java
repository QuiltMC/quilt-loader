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

package org.quiltmc.loader.impl.metadata.qmj;

import java.util.Collection;
import java.util.Collections;

import org.quiltmc.loader.api.ModContributor;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
class ModContributorImpl implements ModContributor {
	private final String name;
	private final Collection<String> roles;

	ModContributorImpl(String name, Collection<String> roles) {
		this.name = name;
		this.roles = Collections.unmodifiableCollection(roles);
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public String role() {
		return roles.isEmpty() ? "" : roles.iterator().next();
	}

	@Override
	public Collection<String> roles() {
		return roles;
	}
}
