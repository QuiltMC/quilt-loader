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

package org.quiltmc.loader.impl.plugin.gui;


import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/**
 * Handles the translation of the plugin's status tree nodes.
 */
// TODO: support plugins having their own language files for their own translation keys
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class I18n {
	private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("lang/quilt_loader", Locale.getDefault(), new LocaleFactory());

	public static String translate(String key) {
		if (BUNDLE.containsKey(key)) {
			return BUNDLE.getString(key);
		} else {
			return key;
		}
	}

	private static final class LocaleFactory extends ResourceBundle.Control {
		@Override
		public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader, boolean reload) throws IllegalAccessException, InstantiationException, IOException {
			String bundleName = this.toBundleName(baseName, locale);
			String resourceName = this.toResourceName(bundleName, "properties");

			try (InputStream stream = loader.getResourceAsStream(resourceName)) {
				if (stream != null) {
					return new PropertyResourceBundle(new InputStreamReader(stream, StandardCharsets.UTF_8));
				}
			}

			return super.newBundle(baseName, locale, format, loader, reload);
		}
	}
}
