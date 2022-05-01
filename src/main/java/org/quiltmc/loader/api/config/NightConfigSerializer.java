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

package org.quiltmc.loader.api.config;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableCommentedConfig;
import com.electronwill.nightconfig.core.io.ConfigParser;
import com.electronwill.nightconfig.core.io.ConfigWriter;
import org.quiltmc.loader.api.config.annotations.Comment;
import org.quiltmc.loader.api.config.values.CompoundConfigValue;
import org.quiltmc.loader.api.config.values.ValueList;
import org.quiltmc.loader.api.config.values.ValueMap;
import org.quiltmc.loader.api.config.values.ValueTreeNode;

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
		this.writer.write(write(CommentedConfig.inMemory(), config.nodes()), to);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	@Override
	public void deserialize(Config config, InputStream from) {
		CommentedConfig read = this.parser.parse(from);

		for (TrackedValue<?> trackedValue : config.values()) {
			if (read.contains(trackedValue.getKey().toString())) {
				((TrackedValue) trackedValue).setValue(coerce(read.get(trackedValue.getKey().toString()), trackedValue.getDefaultValue()), false);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static Object coerce(Object object, Object to) {
		if (to instanceof Integer) {
			return ((Number) object).intValue();
		} else if (to instanceof Long) {
			return ((Number) object).longValue();
		} else if (to instanceof Float) {
			return ((Number) object).floatValue();
		} else if (to instanceof Double) {
			return ((Number) object).doubleValue();
		} else if (to instanceof String) {
			return object;
		} else if (to instanceof Boolean) {
			return object;
		} else if (to instanceof ValueMap) {
			@SuppressWarnings("rawtypes")
			ValueMap.Builder builder = ValueMap.builder(((ValueMap) to).getDefaultValue());

			for (CommentedConfig.Entry entry : ((CommentedConfig) object).entrySet()) {
				builder.put(entry.getKey(), coerce(entry.getValue(), ((ValueMap<?>) to).getDefaultValue()));
			}

			return builder.build();
		} else if (to instanceof ValueList) {
			Object[] values = ((List<?>) object).toArray();

			for (int i = 0; i < values.length; ++i) {
				values[i] = coerce(values[i], ((ValueList<?>) to).getDefaultValue());
			}

			return ValueList.create(((ValueList<?>) to).getDefaultValue(), values);
		} else if (to.getClass().isEnum()) {
			for (Object o : to.getClass().getEnumConstants()) {
				if (((Enum<?>) o).name().equalsIgnoreCase((String) object)) {
					return o;
				}
			}

			throw new RuntimeException("Unexpected value '" + object + "' for enum class '" + to.getClass() + "'");
		} else {
			throw new RuntimeException("Unexpected value type: " + to.getClass());
		}
	}

	private static List<Object> convertList(List<?> list) {
		List<Object> result = new ArrayList<>(list.size());

		for (Object value : list) {
			if (value instanceof ValueMap) {
				result.add(convertMap((ValueMap<?>) value));
			} else if (value instanceof ValueList) {
				result.add(convertList((ValueList<?>) value));
			} else {
				result.add(value);
			}
		}

		return result;
	}

	private static UnmodifiableCommentedConfig convertMap(ValueMap<?> map) {
		CommentedConfig result = CommentedConfig.inMemory();

		for (Map.Entry<String, ?> entry : map.entrySet()) {
			Object value = entry.getValue();

			if (value instanceof ValueMap) {
				value = convertMap((ValueMap<?>) value);
			} else if (value instanceof ValueList) {
				value = convertList((ValueList<?>) value);
			}

			result.add(entry.getKey(), value);
		}

		return result;
	}

	private static Object convertAny(Object value) {
		if (value instanceof ValueMap) {
			return convertMap((ValueMap<?>) value);
		} else if (value instanceof ValueList) {
			return convertList((ValueList<?>) value);
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

				if (!(defaultValue instanceof CompoundConfigValue<?>)) {
					comments.add("default: " + defaultValue);
				}

				config.add(trackedValue.getKey().toString(), convertAny(trackedValue.getRealValue()));
			} else {
				write(config, ((ValueTreeNode.Section) node));
			}

			if (!comments.isEmpty()) {
				config.setComment(node.getKey().toString(), " " + String.join("\n ", comments));
			}
		}

		return config;
	}
}
