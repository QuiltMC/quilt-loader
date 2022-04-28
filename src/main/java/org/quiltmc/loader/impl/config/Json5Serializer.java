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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.quiltmc.json5.JsonReader;
import org.quiltmc.json5.JsonToken;
import org.quiltmc.json5.JsonWriter;
import org.quiltmc.loader.api.config.annotations.Comment;
import org.quiltmc.loader.api.config.values.CompoundConfigValue;
import org.quiltmc.loader.api.config.Config;
import org.quiltmc.loader.api.config.Constraint;
import org.quiltmc.loader.api.config.Serializer;
import org.quiltmc.loader.api.config.TrackedValue;
import org.quiltmc.loader.api.config.values.ValueList;
import org.quiltmc.loader.api.config.values.ValueMap;
import org.quiltmc.loader.api.config.values.ValueTreeNode;
import org.quiltmc.loader.impl.config.tree.TrackedValueImpl;

public final class Json5Serializer implements Serializer {
	public static final Json5Serializer INSTANCE = new Json5Serializer();

	private Json5Serializer() {

	}

	@Override
	public String getFileExtension() {
		return "json5";
	}

	private void serialize(JsonWriter writer, Object value) throws IOException {
		if (value instanceof Integer) {
			writer.value((Integer) value);
		} else if (value instanceof Long) {
			writer.value((Long) value);
		} else if (value instanceof Float) {
			writer.value((Float) value);
		} else if (value instanceof Double) {
			writer.value((Double) value);
		} else if (value instanceof Boolean) {
			writer.value((Boolean) value);
		} else if (value instanceof String) {
			writer.value((String) value);
		} else if (value instanceof ValueList<?>) {
			writer.beginArray();

			for (Object v : (ValueList<?>) value) {
				serialize(writer, v);
			}

			writer.endArray();
		} else if (value instanceof ValueMap<?>) {
			writer.beginObject();

			for (Map.Entry<String, ?> entry : (ValueMap<?>) value) {
				writer.name(entry.getKey());
				serialize(writer, entry.getValue());
			}

			writer.endObject();
		} else if (value == null) {
			writer.nullValue();
		} else {
			throw new RuntimeException();
		}
	}

	private void serialize(JsonWriter writer, ValueTreeNode node) throws IOException {
		for (String comment : node.metadata(Comment.TYPE)) {
			writer.comment(comment);
		}

		if (node instanceof ValueTreeNode.Section) {
			writer.name(node.getKey().getLastComponent());
			writer.beginObject();

			for (ValueTreeNode child : ((ValueTreeNode.Section) node)) {
				serialize(writer, child);
			}

			writer.endObject();
		} else {
			TrackedValue<?> trackedValue = ((TrackedValue<?>) node);
			Object defaultValue = trackedValue.getDefaultValue();

			if (!(defaultValue instanceof CompoundConfigValue<?>)) {
				writer.comment("default: " + defaultValue);
			}

			for (Constraint<?> constraint : trackedValue.constraints()) {
				writer.comment(constraint.getRepresentation());
			}

			writer.name(node.getKey().getLastComponent());

			serialize(writer, trackedValue.getRealValue());
		}
	}

	@Override
	public void serialize(Config config, OutputStream to) throws IOException {
		JsonWriter writer = JsonWriter.json5(new OutputStreamWriter(to));

		for (String comment : config.metadata(Comment.TYPE)) {
			writer.comment(comment);
		}

		writer.beginObject();

		for (ValueTreeNode node : config.nodes()) {
			this.serialize(writer, node);
		}

		writer.endObject();
		writer.close();
	}

	@SuppressWarnings("unchecked")
	private Object coerce(Object object, Object to) {
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

			for (Map.Entry<String, ?> entry : ((Map<String, ?>) object).entrySet()) {
				builder.put(entry.getKey(), this.coerce(entry.getValue(), ((ValueMap<?>) to).getDefaultValue()));
			}

			return builder.build();
		} else if (to instanceof ValueList) {
			Object[] values = ((List<?>) object).toArray();

			for (int i = 0; i < values.length; ++i) {
				values[i] = this.coerce(values[i], ((ValueList<?>) to).getDefaultValue());
			}

			return ValueList.create(((ValueList<?>) to).getDefaultValue(), values);
		} else {
			throw new RuntimeException();
		}
	}

	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void deserialize(Config config, InputStream from) {
		try {
			JsonReader reader = JsonReader.json5(new InputStreamReader(from));

			Map<String, Object> values = parseObject(reader);

			for (TrackedValue<?> value : config.values()) {
				Map<String, Object> m = values;

				for (int i = 0; i < value.getKey().length(); ++i) {
					String k = value.getKey().getKeyComponent(i);

					if (m.containsKey(k) && i != value.getKey().length() - 1) {
						m = (Map<String, Object>) m.get(k);
					} else if (m.containsKey(k)) {
						((TrackedValueImpl) value).setValue(this.coerce(m.get(k), value.getDefaultValue()), false);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static Map<String, Object> parseObject(JsonReader reader) throws IOException {
		reader.beginObject();

		Map<String, Object> object = new LinkedHashMap<>();

		while (reader.hasNext() && reader.peek() == JsonToken.NAME) {
			object.put(reader.nextName(), parseElement(reader));
		}

		reader.endObject();

		return object;
	}

	public static List<Object> parseArray(JsonReader reader) throws IOException {
		reader.beginArray();

		List<Object> array = new ArrayList<>();

		while (reader.hasNext() && reader.peek() != JsonToken.END_ARRAY) {
			array.add(parseElement(reader));
		}

		reader.endArray();

		return array;
	}

	private static Object parseElement(JsonReader reader) throws IOException {
		switch (reader.peek()) {
			case END_ARRAY:
				throw new UnsupportedOperationException("Unexpected end of array");
			case BEGIN_OBJECT:
				return parseObject(reader);
			case BEGIN_ARRAY:
				return parseArray(reader);
			case END_OBJECT:
				throw new RuntimeException("Unexpected end of object");
			case NAME:
				throw new RuntimeException("Unexpected name");
			case STRING:
				return reader.nextString();
			case NUMBER:
				return reader.nextNumber();
			case BOOLEAN:
				return reader.nextBoolean();
			case NULL:
				reader.nextNull();
				return null;
			case END_DOCUMENT:
				throw new RuntimeException("Unexpected end of file");
		}

		throw new RuntimeException("Encountered unknown JSON token");
	}
}
