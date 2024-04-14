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

package org.quiltmc.loader.api.gui;

/** Base interface for all gui windows opened by loader.
 * <p>
 * Common properties:
 * <ul>
 * <li>A title, in {@link QuiltLoaderText} form.</li>
 * <li>An icon, in {@link QuiltLoaderIcon} form.</li>
 * </ul>
 * 
 * @param <R> The return type for this window. This can be obtained with {@link #returnValue()}. */
public interface QuiltLoaderWindow<R> {

	QuiltLoaderText title();

	void title(QuiltLoaderText title);

	QuiltLoaderIcon icon();

	void icon(QuiltLoaderIcon icon);

	R returnValue();

	void returnValue(R value);

	/** Adds a listener that will be invoked when this window is closed by the user. */
	void addClosedListener(Runnable onCloseListener);
}
