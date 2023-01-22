package org.quiltmc.loader.impl.gui;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;

import org.quiltmc.json5.JsonWriter;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.plugin.LoaderValueFactory;

public class QuiltRemoteWindowHelper {

	private static final QuiltIPC IPC;

	static {
		try {
			IPC = QuiltIPC.connect(new File(".quilt/ipc"), QuiltRemoteWindowHelper::handleMessageFromServer);
		} catch (IOException e) {
			throw new Error(e);
		}
	}

	public static void sendProgressUpdate(String state, int percent) {
		sendProgressUpdate(state, percent, 1000);
	}

	public static void sendProgressUpdate(String state, int percent, int delay) {
		Map<String, LoaderValue> map = new HashMap<>();
		map.put("__TYPE", lvf().string("QuiltProgressUpdate"));
		map.put("state", lvf().string(state));
		map.put("percent", lvf().number(percent));
		IPC.send(lvf().object(map));
		if (delay > 0) {
			try {
				Thread.sleep(delay);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public static void close() {
		IPC.send(lvf().nul());
	}

	private static LoaderValueFactory lvf() {
		return LoaderValueFactory.getFactory();
	}

	public static void openErrorGui(QuiltJsonGui tree) {
		Map<String, LoaderValue> map = new HashMap<>();
		map.put("__TYPE", lvf().string("QuiltJsonGui"));
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			tree.write(JsonWriter.json(new OutputStreamWriter(baos)));
			map.put("tree", lvf().read(new ByteArrayInputStream(baos.toByteArray())));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		IPC.send(lvf().object(map));
	}

	private static void handleMessageFromServer(LoaderValue msg) {
		throw new Error("Unhandled message " + msg);
	}
}
