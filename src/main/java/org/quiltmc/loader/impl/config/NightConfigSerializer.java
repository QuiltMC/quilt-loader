/*
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

package org.quiltmc.loader.impl.config;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.InMemoryCommentedFormat;
import com.electronwill.nightconfig.core.UnmodifiableCommentedConfig;
import com.electronwill.nightconfig.core.io.ConfigParser;
import com.electronwill.nightconfig.core.io.ConfigWriter;
import org.quiltmc.config.api.Config;
import org.quiltmc.config.api.Constraint;
import org.quiltmc.config.api.MarshallingUtils;
import org.quiltmc.config.api.Serializer;
import org.quiltmc.config.api.annotations.Comment;
import org.quiltmc.config.api.values.CompoundConfigValue;
import org.quiltmc.config.api.values.ConfigSerializableObject;
import org.quiltmc.config.api.values.TrackedValue;
import org.quiltmc.config.api.values.ValueList;
import org.quiltmc.config.api.values.ValueMap;
import org.quiltmc.config.api.values.ValueTreeNode;

public final class NightConfigSerializer<C extends CommentedConfig> implements Serializer {
	private final String fileExtension;
	private final ConfigParser<C> parser;
	private final ConfigWriter writer;

	public NightConfigSerializer(String fileExtension, ConfigParser<C> parser, ConfigWriter writer) {
		this.fileExtension = fileExtension;
		this.parser = parser;
		this.writer = writer;
	}

	@Override
	public String getFileExtension() {
		return this.fileExtension;
	}

	@Override
	public void serialize(Config config, OutputStream to) {
		this.writer.write(write(createCommentedConfig(), config.nodes()), to);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	@Override
	public void deserialize(Config config, InputStream from) {
		CommentedConfig read = this.parser.parse(from);

		for (TrackedValue<?> trackedValue : config.values()) {
			if (read.contains(trackedValue.key().toString())) {
				((TrackedValue) trackedValue).setValue(MarshallingUtils.coerce(read.get(trackedValue.key().toString()), trackedValue.getDefaultValue(), (CommentedConfig c, MarshallingUtils.MapEntryConsumer entryConsumer) ->
						c.entrySet().forEach(e -> entryConsumer.put(e.getKey(), e.getValue()))), false);
			}
		}
	}

	private static List<Object> convertList(List<?> list) {
		List<Object> result = new ArrayList<>(list.size());

		for (Object value : list) {
			result.add(convertAny(value));
		}

		return result;
	}

	private static UnmodifiableCommentedConfig convertMap(ValueMap<?> map) {
		CommentedConfig result = createCommentedConfig();

		for (Map.Entry<String, ?> entry : map.entrySet()) {
			result.add(entry.getKey(), convertAny(entry.getValue()));
		}

		return result;
	}

	private static Object convertAny(Object value) {
		if (value instanceof ValueMap) {
			return convertMap((ValueMap<?>) value);
		} else if (value instanceof ValueList) {
			return convertList((ValueList<?>) value);
		} else if (value instanceof ConfigSerializableObject) {
			return convertAny(((ConfigSerializableObject<?>) value).getRepresentation());
		} else {
			return value;
		}
	}

	private static CommentedConfig write(CommentedConfig config, Iterable<ValueTreeNode> nodes) {
		for (ValueTreeNode node : nodes) {
			List<String> comments = new ArrayList<>();

			if (node.hasMetadata(Comment.TYPE)) {
				for (String string : node.metadata(Comment.TYPE)) {
					comments.add(string);
				}
			}

			if (node instanceof TrackedValue) {
				TrackedValue<?> trackedValue = (TrackedValue<?>) node;
				Object defaultValue = trackedValue.getDefaultValue();

				if (defaultValue.getClass().isEnum()) {
					StringBuilder options = new StringBuilder("options: ");
					Object[] enumConstants = defaultValue.getClass().getEnumConstants();

					for (int i = 0, enumConstantsLength = enumConstants.length; i < enumConstantsLength; i++) {
						Object o = enumConstants[i];

						options.append(o);

						if (i < enumConstantsLength - 1) {
							options.append(", ");
						}
					}

					comments.add(options.toString());
				}

				for (Constraint<?> constraint : trackedValue.constraints()) {
					comments.add(constraint.getRepresentation());
				}

				if (!(defaultValue instanceof CompoundConfigValue<?>)) {
					comments.add("default: " + defaultValue);
				}

				config.add(trackedValue.key().toString(), convertAny(trackedValue.getRealValue()));
			} else {
				write(config, ((ValueTreeNode.Section) node));
			}

			if (!comments.isEmpty()) {
				config.setComment(node.key().toString(), " " + String.join("\n ", comments));
			}
		}

		return config;
	}

	private static CommentedConfig createCommentedConfig() {
		return InMemoryCommentedFormat.defaultInstance().createConfig(LinkedHashMap::new);
	}
}
