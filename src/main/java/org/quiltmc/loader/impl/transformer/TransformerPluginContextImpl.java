/*
 * Copyright 2025 QuiltMC
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

package org.quiltmc.loader.impl.transformer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.quiltmc.loader.api.plugin.transformer.QuiltTransformerPlugin;
import org.quiltmc.loader.api.plugin.transformer.QuiltTransformerPluginContext;
import org.quiltmc.loader.api.plugin.transformer.TransformCache;
import org.quiltmc.loader.impl.transformer.phase.PhaseData;
import org.quiltmc.loader.impl.transformer.phase.PhaseSorting;

class TransformerPluginContextImpl implements QuiltTransformerPluginContext {

	private final Map<String, TransformPhaseData> ids = new HashMap<>();

	TransformerPluginContextImpl() {
		// register the root phase with a dummy consumer
		TransformPhaseData root = new TransformPhaseData(QuiltTransformerPlugin.ROOT);
		root.setData(t -> {});
		ids.put(QuiltTransformerPlugin.ROOT, new TransformPhaseData(QuiltTransformerPlugin.ROOT));
	}

	
	List<TransformPhaseData> sort() {
		List<TransformPhaseData> ret = new ArrayList<>(ids.values());
		PhaseSorting.sortPhases(ret);
		return ret;
	}

	@Override
	public void registerInput(String key, String value) {
		throw new UnsupportedOperationException("Unimplemented method 'registerInput'");
	}

	@Override
	public TransformBuilder createTransformation(String id) {
		return new BuilderImpl(id);
	}
	
	class BuilderImpl implements QuiltTransformerPluginContext.TransformBuilder {
    final String id;
		final List<String> before = new ArrayList<>();
		final List<String> after = new ArrayList<>();

		public BuilderImpl(String id) {
			this.id = id;
		}

		@Override
		public TransformBuilder before(String id) {
			this.before.add(id);
			return this;
		}

		@Override
		public TransformBuilder after(String id) {
			this.after.add(id);
			return this;
		}

		@Override
		public void register(TransformCacheConsumer consumer) {
			TransformPhaseData thisPhase = ids.computeIfAbsent(this.id, TransformPhaseData::new);
			// We mark phases we haven't seen a plugin for with null.
			if (thisPhase.getData() != null) {
				throw new IllegalStateException("Cannot register two transformation steps with the same id!");
			}
			thisPhase.setData(consumer);
			boolean beforeRoot = false;
			for (String other : before) {
				if (other.equals(QuiltTransformerPlugin.ROOT)) {
					beforeRoot = true;
				}
				PhaseData.link(thisPhase, ids.computeIfAbsent(other, TransformPhaseData::new));
			}
			if (!beforeRoot) {
				// implicitly run after root
				PhaseData.link(thisPhase, ids.get(QuiltTransformerPlugin.ROOT));
			}
			for (String other : after) {
				PhaseData.link(ids.computeIfAbsent(other, TransformPhaseData::new), thisPhase);
			}
			
		}
	}
}
