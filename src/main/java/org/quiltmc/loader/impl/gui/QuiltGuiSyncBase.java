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

package org.quiltmc.loader.impl.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.LoaderValue.LObject;
import org.quiltmc.loader.api.LoaderValue.LType;
import org.quiltmc.loader.api.plugin.LoaderValueFactory;
import org.quiltmc.loader.impl.util.LoaderValueHelper;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;


/** Helper class for quilt gui objects which need to have state synced from client to server. */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
abstract class QuiltGuiSyncBase {

	static final AtomicInteger IDS;
	static final LoaderValueHelper<IOException> HELPER = LoaderValueHelper.IO_EXCEPTION;
	static final Map<Integer, QuiltGuiSyncBase> ALL_OBJECTS = new ConcurrentHashMap<>();

	static {
		if (QuiltForkComms.isServer()) {
			IDS = new AtomicInteger(Integer.MIN_VALUE);
		} else {
			IDS = new AtomicInteger();
		}
	}

	enum WriteState {
		NOT_YET,
		STARTED,
		FINISHED;
	}

	interface Listener {}

	final QuiltGuiSyncBase parent;
	final int id;
	Map<Integer, QuiltGuiSyncBase> children;
	private WriteState writeState = WriteState.NOT_YET;

	final List<Listener> listeners = new ArrayList<>();

	private QuiltGuiSyncBase(QuiltGuiSyncBase parent, int id) {
		this.parent = parent;
		this.id = id;
		ALL_OBJECTS.put(id, this);
		if (parent != null) {
			synchronized (parent) {
				if (parent.children == null) {
					parent.children = new HashMap<>();
				}
				parent.children.put(id, this);
			}
		}
	}

	public QuiltGuiSyncBase(QuiltGuiSyncBase parent) {
		this(parent, IDS.incrementAndGet());
	}

	public QuiltGuiSyncBase(QuiltGuiSyncBase parent, LoaderValue.LObject obj) throws IOException {
		this(parent, HELPER.expectNumber(obj, "id").intValue());
		String readType = HELPER.expectString(obj, "syncType");
		String expectedType = syncType();
		if (!readType.equals(expectedType)) {
			throw new IOException("Expected '" + expectedType + "', but got '" + readType + "' " + obj);
		}
	}

	static void createObject(QuiltGuiSyncBase parent, LObject packet) throws IOException {
		// Normally it's a bad idea to deserialise an unknown class from the network
		// However since this only accepts connections to/from localhost rather than the wider network this is okay
		String className = HELPER.expectString(packet, "class");
		Class<?> cls;
		try {
			cls = Class.forName(className);
		} catch (ClassNotFoundException e) {
			throw new IOException("Unknown remote class '" + className + "'");
		}

		if (!QuiltGuiSyncBase.class.isAssignableFrom(cls)) {
			throw new IOException("Bad remote class '" + className + "'");
		}

		Object obj = createObject(parent, cls, HELPER.expectObject(packet, "data"));

		if (parent != null) {
			parent.onChildAdded((QuiltGuiSyncBase) obj);
		}
	}

	private static <Q> Q createObject(QuiltGuiSyncBase parent, Class<Q> cls, LObject data) throws IOException {
		try {
			Class<?>[] args = { QuiltGuiSyncBase.class, LoaderValue.LObject.class };
			Q q = cls.getDeclaredConstructor(args).newInstance(parent, data);
			// Object construction handles putting the value
			// In addition we don't actually *do* anything else yet.
			assert ALL_OBJECTS.containsValue((QuiltGuiSyncBase) q);
			return q;
		} catch (ReflectiveOperationException e) {
			throw new IOException(e);
		}
	}

	/** Called when the child is created with this as it's parent, but NOT when it's created from
	 * {@link #readChild(LoaderValue, Class)} and the child hasn't already been sent. */
	void onChildAdded(QuiltGuiSyncBase child) {
		
	}

	static void updateObject(LObject obj) throws IOException {
		int id = HELPER.expectNumber(obj, "id").intValue();
		String name = HELPER.expectString(obj, "name");
		LObject data = HELPER.expectObject(obj, "data");
		QuiltGuiSyncBase sync = ALL_OBJECTS.get(id);
		if (sync == null) {
			throw new IOException("Unknown remote object " + id);
		}
		String readType = HELPER.expectString(obj, "syncType");
		String expectedType = sync.syncType();
		if (!readType.equals(expectedType)) {
			throw new IOException("Expected '" + expectedType + "', but got '" + readType + "' " + obj);
		}
		sync.handleUpdate(name, data);
	}

	final void sendSignal(String name) {
		sendUpdate(name, lvf().object(Collections.emptyMap()));
	}

	final void sendUpdate(String name, LObject data) {
		Map<String, LoaderValue> map = new HashMap<>();
		map.put("__TYPE", lvf().string(ForkCommNames.ID_GUI_OBJECT_UPDATE));
		map.put("id", lvf().number(id));
		map.put("name", lvf().string(name));
		map.put("syncType", lvf().string(syncType()));
		map.put("data", data);

		QuiltFork.sendRaw(lvf().object(map));
	}

	void handleUpdate(String name, LObject data) throws IOException {
		throw new IOException("Unknown remote update '" + name + "' in " + getClass());
	}

	/** Used for making sure we don't get desynced. */
	abstract String syncType();

	public final void send() {
		if (writeState != WriteState.NOT_YET) {
			return;
		}
		Map<String, LoaderValue> map = new HashMap<>();
		map.put("__TYPE", lvf().string(ForkCommNames.ID_GUI_OBJECT_CREATE));
		map.put("class", lvf().string(getClass().getName()));
		map.put("data", write());
		QuiltFork.sendRaw(lvf().object(map));
	}

	public final LoaderValue.LObject write() {
		writeState = WriteState.STARTED;
		Map<String, LoaderValue> map = new HashMap<>();
		map.put("id", lvf().number(id));
		map.put("syncType", lvf().string(syncType()));
		write0(map);
		writeState = WriteState.FINISHED;
		return lvf().object(map);
	}

	protected abstract void write0(Map<String, LoaderValue> map);

	protected final LoaderValue[] write(Collection<? extends QuiltGuiSyncBase> from) {
		int i = 0;
		LoaderValue[] array = new LoaderValue[from.size()];
		for (QuiltGuiSyncBase sync : from) {
			array[i++] = writeChild(sync);
		}
		return array;
	}

	protected final LoaderValue writeChild(QuiltGuiSyncBase sync) {
		if (sync == null) {
			throw new NullPointerException();
		}

		if (sync.parent == this && writeState == WriteState.STARTED && sync.writeState == WriteState.NOT_YET) {
			return sync.write();
		} else {
			sync.send();
			return lvf().number(sync.id);
		}
	}

	protected <T extends QuiltGuiSyncBase> T readChild(LoaderValue value, Class<T> clazz) throws IOException {
		if (value.type() == LType.NUMBER) {
			int id = value.asNumber().intValue();
			QuiltGuiSyncBase sync = ALL_OBJECTS.get(id);
			if (sync == null) {
				throw new IOException("Unknown remote object " + id);
			}
			if (clazz.isInstance(sync)) {
				return clazz.cast(sync);
			} else {
				throw new IOException(sync + " is not an instance of " + clazz);
			}
		} else {
			return createObject(this, clazz, HELPER.expectObject(value));
		}
	}

	static final LoaderValueFactory lvf() {
		return LoaderValueFactory.getFactory();
	}

	final boolean shouldSendUpdates() {
		return writeState == WriteState.FINISHED;
	}

	<L> void invokeListeners(Class<L> clazz, Consumer<L> invoker) {
		for (Object listener : this.listeners) {
			if (clazz.isInstance(listener)) {
				invoker.accept(clazz.cast(listener));
			}
		}
	}
}
