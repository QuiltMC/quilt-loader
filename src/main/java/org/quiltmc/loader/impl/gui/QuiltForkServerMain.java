package org.quiltmc.loader.impl.gui;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.LoaderValue.LObject;
import org.quiltmc.loader.api.LoaderValue.LType;
import org.quiltmc.loader.api.plugin.LoaderValueFactory;
import org.quiltmc.loader.impl.util.LoaderValueHelper;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class QuiltForkServerMain {

	private static final Map<Integer, NavigableMap<Integer, BufferedImage>> ICONS = new ConcurrentHashMap<>();
	private static QuiltForkServerMain currentConnection;

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

	static void sendRaw(LoaderValue.LObject packet) {
		currentConnection.comms.send(packet);
	}

 	public static NavigableMap<Integer, BufferedImage> getCustomIcon(int index) {
 		return ICONS.getOrDefault(index, Collections.emptyNavigableMap());
 	}

	/* TODO:
	 * - Make a "holding class" (and thread, possibly the main thread) which should stay alive [done]
	 * - Change the current error gui window to open via this instead
	 */

	final QuiltForkComms comms;

	private QuiltForkServerMain(Socket connection) {
		currentConnection = this;
		comms = new QuiltForkComms(connection, this::handleMessage);
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
			comms.close();
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
			case ForkCommNames.ID_EXCEPTION: {
				handleException(obj);
				break;
			}
			case ForkCommNames.ID_GUI_OBJECT_CREATE: {
				QuiltGuiSyncBase.createObject(null, obj);
				break;
			}
			case ForkCommNames.ID_GUI_OBJECT_UPDATE: {
				QuiltGuiSyncBase.updateObject(obj);
				break;
			}
			case ForkCommNames.ID_UPLOAD_ICON: {
				handleUploadedIcon(obj);
				break;
			}
			default: {
				System.err.println("Unknown json message type '" + value + "'");
				throw new Error("Wrong type! " + value);
			}
		}
	}

	private void handleException(LObject packet) {
		// The *client* crashed while handling an exception
		// Since the client will handle printing the exception we'll just exit
		comms.close();
		// TODO: Open an error window with the client error!
		System.exit(1);
	}

	private void handleUploadedIcon(LObject packet) throws IOException {
		LoaderValueHelper<IOException> helper = LoaderValueHelper.IO_EXCEPTION;
		int index = helper.expectNumber(packet, "index").intValue();
		LObject images = helper.expectObject(packet, "images");
		NavigableMap<Integer, BufferedImage> imageMap = new TreeMap<>();
		for (Map.Entry<String, LoaderValue> entry : images.entrySet()) {
			int resolution;
			try {
				resolution = Integer.parseInt(entry.getKey());
			} catch (NumberFormatException e) {
				throw new IOException("Cannot convert '" + entry.getKey() + "' into a number!", e);
			}
			String base64 = helper.expectString(entry.getValue());
			BufferedImage img = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
			imageMap.put(resolution, img);
		}
		ICONS.put(index, Collections.unmodifiableNavigableMap(imageMap));
	}

	private void loopUntilClosed() {
		// This exists solely to keep the server alive until the connection is closed
		// since every other thread is a daemon thread
		// (Swing windows don't count since we might not have a window open all the time)
		long lastCollect = System.currentTimeMillis();
		while (!comms.isClosed()) {
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
