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

package org.quiltmc.loader.impl.metadata.qmj;

import java.io.IOException;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.json5.JsonReader;
import org.quiltmc.json5.JsonToken;
import org.quiltmc.json5.exception.MalformedSyntaxException;
import org.quiltmc.json5.exception.ParseException;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
interface JsonLoaderValue extends LoaderValue {
	static JsonLoaderValue read(JsonReader reader) throws IOException, ParseException {
		switch (reader.peek()) {
		case BEGIN_ARRAY: {
			String location = reader.locationString();

			reader.beginArray();

			List<LoaderValue> elements = new ArrayList<>();

			while (reader.hasNext()) {
				elements.add(read(reader));
			}

			reader.endArray();

			return new ArrayImpl(location, elements);
		}
		case BEGIN_OBJECT: {
			String location = reader.locationString();

			reader.beginObject();

			Map<String, LoaderValue> elements = new LinkedHashMap<>();

			while (reader.hasNext()) {
				if (reader.peek() != JsonToken.NAME) {
					throw new MalformedSyntaxException(reader, "Entry in object had an entry with no key");
				}

				String key = reader.nextName();
				elements.put(key, read(reader));
			}

			reader.endObject();

			return new ObjectImpl(location, elements);
		}
		case STRING:
			return new StringImpl(reader.locationString(), reader.nextString());
		case NUMBER:
			return new NumberImpl(reader.locationString(), reader.nextNumber());
		case BOOLEAN:
			return new BooleanImpl(reader.locationString(), reader.nextBoolean());
		case NULL:
			String location = reader.locationString();
			reader.nextNull();
			return new NullImpl(location);
		// Invalid
		case NAME:
			throw new MalformedSyntaxException(reader, "Unexpected name encountered");
		case END_ARRAY:
			throw new MalformedSyntaxException(reader, "Unexpected array end encountered");
		case END_OBJECT:
			throw new MalformedSyntaxException(reader, "Unexpected object end encountered");
		case END_DOCUMENT:
			throw new ParseException(reader, "Encountered end of document");
		}

		throw new UnsupportedOperationException("Encountered unreachable state");
	}

	/**
	 * @return the location of this loader value in the originating json file.
	 */
	@Override
	String location();

	@Override
	default ObjectImpl asObject() {
		if (this instanceof ObjectImpl) {
			return (ObjectImpl) this;
		}

		throw new ClassCastException("Cannot coerce loader value to an Object");
	}

	@Override
	default ArrayImpl asArray() {
		if (this instanceof ArrayImpl) {
			return ((ArrayImpl) this);
		}

		throw new ClassCastException("Cannot coerce loader value to an Array");
	}

	@Override
	default String asString() {
		if (this instanceof StringImpl) {
			return ((StringImpl) this).value;
		}

		throw new ClassCastException("Cannot coerce loader value to a String");
	}

	@Override
	default Number asNumber() {
		if (this instanceof NumberImpl) {
			return ((NumberImpl) this).value;
		}

		throw new ClassCastException("Cannot coerce loader value to a Number");
	}

	@Override
	default boolean asBoolean() {
		if (this instanceof BooleanImpl) {
			return ((BooleanImpl) this).value;
		}

		throw new ClassCastException("Cannot coerce loader value to a boolean");
	}

	final class StringImpl implements JsonLoaderValue {
		private final String location;
		private final String value;

		StringImpl(String location, String value) {
			this.location = location;
			this.value = value;
		}

		@Override
		public LType type() {
			return LType.STRING;
		}

		@Override
		public String location() {
			return this.location;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof StringImpl && value.equals(((StringImpl) obj).value);
		}

		@Override
		public int hashCode() {
			return value.hashCode();
		}
	}

	final class NumberImpl implements JsonLoaderValue {
		private final String location;
		private final Number value;

		NumberImpl(String location, Number value) {
			this.location = location;
			this.value = value;
		}

		@Override
		public LType type() {
			return LType.NUMBER;
		}

		@Override
		public String location() {
			return this.location;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof NumberImpl && value.equals(((NumberImpl) obj).value);
		}

		@Override
		public int hashCode() {
			return value.hashCode();
		}
	}

	final class BooleanImpl implements JsonLoaderValue {
		private final String location;
		private final boolean value;

		BooleanImpl(String location, boolean value) {
			this.location = location;
			this.value = value;
		}

		@Override
		public LType type() {
			return LType.BOOLEAN;
		}

		@Override
		public String location() {
			return this.location;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof BooleanImpl && value == ((BooleanImpl) obj).value;
		}

		@Override
		public int hashCode() {
			return Boolean.hashCode(value);
		}
	}

	final class ObjectImpl extends AbstractMap<String, LoaderValue> implements JsonLoaderValue, LObject {
		private final String location;
		private final Map<String, LoaderValue> value;

		ObjectImpl(String location, Map<String, LoaderValue> value) {
			this.location = location;
			this.value = Collections.unmodifiableMap(value);
		}

		@Override
		public LType type() {
			return LType.OBJECT;
		}

		@Override
		public String location() {
			return this.location;
		}

		@Override
		public Set<Entry<String, LoaderValue>> entrySet() {
			return this.value.entrySet();
		}

		// Implement some high traffic methods

		@Nullable
		@Override
		public JsonLoaderValue get(Object key) {
			return (JsonLoaderValue) this.value.get(key);
		}

		@Override
		public boolean isEmpty() {
			return this.value.isEmpty();
		}

		@Override
		public int size() {
			return this.value.size();
		}

		@Override
		public Set<String> keySet() {
			return this.value.keySet();
		}

		@Override
		public Collection<LoaderValue> values() {
			return this.value.values();
		}
	}

	final class ArrayImpl extends AbstractList<LoaderValue> implements JsonLoaderValue, LArray {
		private final String location;
		private final List<LoaderValue> value;

		ArrayImpl(String location, List<LoaderValue> value) {
			this.location = location;
			this.value = Collections.unmodifiableList(value);
		}

		@Override
		public LType type() {
			return LType.ARRAY;
		}

		@Override
		public String location() {
			return this.location;
		}

		@Override
		public JsonLoaderValue get(int i) {
			return (JsonLoaderValue) this.value.get(i);
		}

		@Override
		public int size() {
			return this.value.size();
		}

		// Implement some high traffic methods

		// For enhanced for loops
		@Override
		public Iterator<LoaderValue> iterator() {
			return this.value.iterator();
		}

		// Some people do use streams
		@Override
		public Stream<LoaderValue> stream() {
			return this.value.stream();
		}
	}

	final class NullImpl implements JsonLoaderValue {
		private final String location;

		NullImpl(String location) {
			this.location = location;
		}

		@Override
		public LType type() {
			return LType.NULL;
		}

		@Override
		public String location() {
			return this.location;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof NullImpl;
		}

		@Override
		public int hashCode() {
			return 0;
		}
	}
}
