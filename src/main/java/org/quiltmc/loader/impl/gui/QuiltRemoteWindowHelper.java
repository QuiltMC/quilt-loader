package org.quiltmc.loader.impl.gui;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.quiltmc.json5.JsonWriter;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.LoaderValue.LObject;
import org.quiltmc.loader.api.LoaderValue.LType;
import org.quiltmc.loader.api.plugin.LoaderValueFactory;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.game.GameProvider;
import org.quiltmc.loader.impl.util.LoaderValueHelper;

public class QuiltRemoteWindowHelper {

	private static final QuiltForkComms COMMS;
	private static final LoaderValueHelper<IOException> HELPER = LoaderValueHelper.IO_EXCEPTION;
	private static final AtomicInteger ERROR_GUI_COUNT = new AtomicInteger();
	private static final Map<Integer, CompletableFuture<Void>> ERROR_GUI_CLOSERS = new ConcurrentHashMap<>();

	static {
		GameProvider provider = QuiltLoaderImpl.INSTANCE.getGameProvider();
		if (!provider.canOpenGui()) {
			COMMS = null;
		} else {
			try {
				COMMS = QuiltForkComms.connect(new File(".quilt/comms"), QuiltFork::handleMessageFromServer);
			} catch (IOException e) {
				throw new Error(e);
			}
		}
	}

	public static void close() {
		if (COMMS != null) {
			COMMS.send(lvf().nul());
		}
	}

	private static LoaderValueFactory lvf() {
		return LoaderValueFactory.getFactory();
	}

	public static void openErrorGui(QuiltJsonGui tree, boolean shouldWait) throws Exception {
		if (COMMS == null) {
			return;
		}
		Map<String, LoaderValue> map = new HashMap<>();
		map.put("__TYPE", lvf().string(IPC_Names.ID_OPEN_ERROR_GUI));
		Integer index = ERROR_GUI_COUNT.incrementAndGet();
		map.put("id", lvf().number(index));
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			JsonWriter writer = JsonWriter.json(new OutputStreamWriter(baos));
			tree.write(writer);
			writer.flush();
			File file = new File("temp.json");
			Files.write(file.toPath(), baos.toByteArray());
			System.out.println(file.getAbsolutePath());
			map.put("tree", lvf().read(new ByteArrayInputStream(baos.toByteArray())));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}

		CompletableFuture<Void> future = new CompletableFuture<>();
		ERROR_GUI_CLOSERS.put(index, future);

		COMMS.send(lvf().object(map));

		if (shouldWait) {
			future.get();
		}
	}

	private static void handleMessageFromServer(LoaderValue msg) {
		try {
			handleMessageFromServer0(msg);
		} catch (IOException io) {
			String json = "<failed to write read json>";
			try {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				LoaderValueFactory.getFactory().write(msg, baos);
				json = new String(baos.toByteArray(), StandardCharsets.UTF_8);
			} catch (IOException e) {
				io.addSuppressed(e);
			}
			throw new Error("Failed to handle json message\n" + json, io);
		}
	}

	private static void handleMessageFromServer0(LoaderValue msg) throws IOException {
		if (msg.type() == LType.NULL) {
			return;
		} else if (msg.type() == LType.OBJECT) {
			LObject obj = msg.asObject();
			String type = HELPER.expectString(obj, "__TYPE");
			switch (type) {
				case IPC_Names.ID_ERROR_GUI_CLOSED: {
					handleCloseErrorGui(obj);
					return;
				}
			}
		}
		throw new Error("Unhandled message " + msg);
	}

	private static void handleCloseErrorGui(LObject obj) throws IOException {
		int id = HELPER.expectNumber(obj, "id").intValue();
		ERROR_GUI_CLOSERS.get(id).complete(null);
	}
}
