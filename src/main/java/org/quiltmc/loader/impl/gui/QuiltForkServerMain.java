package org.quiltmc.loader.impl.gui;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.internal.bind.JsonTreeReader;

import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.LoaderValue.LObject;
import org.quiltmc.loader.api.LoaderValue.LType;
import org.quiltmc.loader.api.plugin.LoaderValueFactory;
import org.quiltmc.loader.impl.util.LoaderValueHelper;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class QuiltForkServerMain {
	public static void main(String[] args) {
		if (args.length < 2 || !"--file".equals(args[0])) {
			System.err.println("QUILT_IPC_SERVER: missing arguments / first argument wasn't a file!");
			System.exit(1);
			return;
		}

		try {
			run(args);
		} catch (IOException io) {
			System.err.println("QUILT_IPC_SERVER: Failed to run!");
			io.printStackTrace();
			System.exit(2);
			return;
		}
	}

	private static void run(String[] args) throws IOException {
		File portFile = new File(args[1] + ".port");
		File readyFile = new File(args[1] + ".ready");

		if (portFile.exists()) {
			System.err.println("QUILT_IPC_SERVER: IPC file already exists" + portFile);
			System.exit(3);
			return;
		}

		ServerSocket socket = new ServerSocket(0, 0, InetAddress.getByName(null));
		int port = socket.getLocalPort();
		System.out.println("Port = " + port);
		byte[] bytes = { //
			(byte) ((port >>> 24) & 0xFF), //
			(byte) ((port >>> 16) & 0xFF), //
			(byte) ((port >>> 8) & 0xFF), //
			(byte) ((port >>> 0) & 0xFF), //
		};
		Files.write(portFile.toPath(), bytes);
		Files.write(readyFile.toPath(), new byte[0]);
		portFile.deleteOnExit();
		readyFile.deleteOnExit();
		Socket connection = socket.accept();
		QuiltForkServerMain server = new QuiltForkServerMain(connection);
		server.loopUntilClosed();
	}

	/* TODO:
	 * - Make a "holding class" (and thread, possibly the main thread) which should stay alive [done]
	 * - Change the current error gui window to open via this instead
	 */

	final QuiltForkComms ipc;

	private QuiltForkServerMain(Socket connection) {
		ipc = new QuiltForkComms(connection, this::handleMessage);
	}

	private void handleMessage(LoaderValue msg) {
		try {
			handleMessage0(msg);
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

	private void handleMessage0(LoaderValue value) throws IOException {
		if (value.type() == LType.NULL) {
			ipc.close();
			System.exit(0);
			return;
		}
		// TODO: Handle exceptions!
		LObject obj = value.asObject();
		LoaderValue type = obj.get("__TYPE");
		if (type == null || type.type() != LType.STRING) {
			throw new IllegalArgumentException("Expected a string, but got " + type + " for '__TYPE'");
		}
		String typeStr = type.asString();

		switch (typeStr) {
			case ForkCommNames.ID_OPEN_ERROR_GUI: {
				handleOpenErrorGui(obj);
				break;
			}
			default: {
				throw new Error("Wrong type! " + value);
			}
		}
	}

	private void handleOpenErrorGui(LObject obj) throws IOException {
		LoaderValueHelper<IOException> helper = LoaderValueHelper.IO_EXCEPTION;
		Number id = helper.expectNumber(obj, "id");
		QuiltJsonGui jsonTree = new QuiltJsonGui(helper.expectObject(obj, "tree"));
		CompletableFuture<Void> future;
		try {
			future = QuiltMainWindow.open(jsonTree, false);
		} catch (Exception e) {
			throw new Error("Failed to open the error gui!", e);
		}
		future.thenRun(() -> {
			Map<String, LoaderValue> map = new HashMap<>();
			LoaderValueFactory lvf = LoaderValueFactory.getFactory();
			map.put("__TYPE", lvf.string(ForkCommNames.ID_ERROR_GUI_CLOSED));
			map.put("id", lvf.number(id));
			ipc.send(lvf.object(map));
		});
	}

	private void loopUntilClosed() {
		// This exists solely to keep the server alive until the connection is closed
		// since every other thread is a daemon thread
		// (Swing windows don't count since we might not have a window open all the time)
		long lastCollect = System.currentTimeMillis();
		while (!ipc.isClosed()) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				System.out.println("Interrupted!");
				break;
			}

			long now = System.currentTimeMillis();
			if (lastCollect + 10000 < now) {
				lastCollect = now;
				System.gc();
			}
		}
	}
}
