/*
 * Copyright 2016 FabricMC
 * Copyright 2022 QuiltMC
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

package net.fabricmc.loader.impl.util;

import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.impl.ModContainerImpl;

/** @deprecated Please don't use internal quilt classes, but if you need
 *             to then use quilt's {@link org.quiltmc.loader.impl.util.DefaultLanguageAdapter} instead. */
@Deprecated
public final class DefaultLanguageAdapter implements LanguageAdapter {
	public static final DefaultLanguageAdapter INSTANCE = new DefaultLanguageAdapter();

	private DefaultLanguageAdapter() {}

	@Override
	public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
		org.quiltmc.loader.api.ModContainer quiltMod = ((ModContainerImpl) mod).getQuiltModContainer();
		try {
			return org.quiltmc.loader.impl.util.DefaultLanguageAdapter.INSTANCE.create(quiltMod, value, type);
		} catch (org.quiltmc.loader.api.LanguageAdapterException e) {
			throw new LanguageAdapterException(e);
		}
	}
}
