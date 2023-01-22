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

package org.quiltmc.loader.impl.report;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class QuiltStringSection extends QuiltReportSection {

	private final List<String> lines = new ArrayList<>();

	public QuiltStringSection(String name, int ordering, String... lines) {
		super(ordering, name);
		Collections.addAll(this.lines, lines);
	}

	public void lines(String... lines) {
		Collections.addAll(this.lines, lines);
	}

	@Override
	public void write(PrintWriter to) {
		for (String line : lines) {
			to.println(line);
		}
	}
}
