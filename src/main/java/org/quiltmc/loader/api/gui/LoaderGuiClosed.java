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

/** Thrown by {@link QuiltLoaderGui#openErrorGui} if the user closed the error gui without actually fixing the error.
 * This generally means the game should crash. */
public final class LoaderGuiClosed extends Exception {
	public static final LoaderGuiClosed INSTANCE = new LoaderGuiClosed();

	private LoaderGuiClosed() {
		super(null, null, false, false);
	}
}
