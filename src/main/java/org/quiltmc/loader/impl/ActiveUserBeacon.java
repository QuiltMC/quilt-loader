package org.quiltmc.loader.impl;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.impl.gui.QuiltForkComms;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
class ActiveUserBeacon {

	private static final String ENVIRONMENT = "QUILT_LOADER_DISABLE_BEACON";
	private static final String ENVIRONMENT_CI = "CI";

	private static final String INFO_SUFFIX = " # Last sent month for the active user beacon. See [BLOG_LINK] for details.";

	static Path configFile;
	static byte[] thisMonthBytes = new byte[0];

	static void run() {
		if (Boolean.getBoolean(SystemProperties.DISABLE_BEACON)) {
			return;
		}
		if (Boolean.parseBoolean(getEnv(ENVIRONMENT))) {
			return;
		}
		if (Boolean.parseBoolean(getEnv(ENVIRONMENT_CI))) {
			return;
		}

		// Just in case
		if (QuiltForkComms.isServer()) {
			return;
		}

		Path globalConfig = QuiltLoader.getGlobalConfigDir();
		configFile = globalConfig.resolve(QuiltLoaderImpl.MOD_ID).resolve("ActiveUserBeacon.txt");
		thisMonthBytes = LocalDateTime.now().getMonth().toString().getBytes(StandardCharsets.UTF_8);
		if (Files.exists(configFile)) {
			try (InputStream stream = Files.newInputStream(configFile)) {
				boolean different = false;
				for (int i = 0; i < thisMonthBytes.length; i++) {
					byte expected = thisMonthBytes[0];
					int got = stream.read();
					if (expected != got) {
						different = true;
						break;
					}
				}
				if (!different) {
					return;
				}
			} catch (IOException io) {
				throw new Error("Failed to read " + globalConfig, io);
			}
		}

		Thread thread = new Thread(ActiveUserBeacon::runOnThread, "Quilt Loader Active User Beacon");
		thread.setDaemon(true);
		thread.start();

		thread.getId();
	}

	private static String getEnv(String key) {
		String value = System.getenv(key);
		if (value == null) {
			return "";
		}
		return value;
	}

	private static void runOnThread() {
		try {
			URL url = new URL("https://beacon.quiltmc.org/signal");

			HttpURLConnection connection = (HttpURLConnection) url.openConnection();

			// Replace the java version with one that doesn't include versioning information
			connection.setRequestProperty("User-Agent", "Quilt-Loader");

			connection.setRequestMethod("POST");
			connection.getResponseCode();

			if (!Files.isDirectory(configFile.getParent())) {
				Files.createDirectories(configFile.getParent());
			}

			byte[] info = INFO_SUFFIX.getBytes(StandardCharsets.UTF_8);
			byte[] total = new byte[thisMonthBytes.length + info.length];
			System.arraycopy(thisMonthBytes, 0, total, 0, thisMonthBytes.length);
			System.arraycopy(info, 0, total, thisMonthBytes.length, info.length);
			Files.write(configFile, total);

		} catch (IOException e) {
			Log.warn(LogCategory.GENERAL, "Failed to notify the beacon - trying again next launch.", e);
			return;
		}
	}
}
