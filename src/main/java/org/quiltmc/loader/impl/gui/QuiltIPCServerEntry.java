package org.quiltmc.loader.impl.gui;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;

import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.LoaderValue.LObject;
import org.quiltmc.loader.api.LoaderValue.LType;

public class QuiltIPCServerEntry {
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
		QuiltIPCServerEntry server = new QuiltIPCServerEntry(connection);
		server.loopUntilClosed();
	}

	/* TODO:
	 * - Make a "holding class" (and thread, possibly the main thread) which should stay alive [done]
	 * - Change the current error gui window to open via this instead
	 */

	final QuiltIPC ipc;

	private QuiltIPCServerEntry(Socket connection) {
		ipc = new QuiltIPC(connection, this::handleMessage);
	}

	private void handleMessage(LoaderValue value) {
		if (value.type() == LType.NULL) {
			ipc.close();
			System.exit(0);
			return;
		}
		// TODO: Handle exceptions!
		LObject obj = value.asObject();
		LoaderValue type = obj.remove("__TYPE");
		if (type == null || type.type() != LType.STRING) {
			throw new IllegalArgumentException("Expected a string, but got " + type + " for '__TYPE'");
		}
		String typeStr = type.asString();

		if ("QuiltLoader:OpenErrorGui".equals(typeStr)) {
			
		} else {
			throw new Error("Wrong type! " + value);
		}
	}

	private void loopUntilClosed() {
		// This exists solely to keep the server alive until the connection is closed
		// since every other thread is a daemon thread
		// (Swing windows don't count since we might not have a window open all the time)
		while (!ipc.isClosed()) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				System.out.println("Interrupted!");
				break;
			}
		}
	}
}
