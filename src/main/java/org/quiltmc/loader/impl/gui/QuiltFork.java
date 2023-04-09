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

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.quiltmc.json5.JsonWriter;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.LoaderValue.LObject;
import org.quiltmc.loader.api.LoaderValue.LType;
import org.quiltmc.loader.api.gui.LoaderGuiClosed;
import org.quiltmc.loader.api.gui.LoaderGuiException;
import org.quiltmc.loader.api.gui.QuiltDisplayedError;
import org.quiltmc.loader.api.gui.QuiltLoaderGui;
import org.quiltmc.loader.api.gui.QuiltLoaderIcon;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.plugin.LoaderValueFactory;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.game.GameProvider;
import org.quiltmc.loader.impl.gui.QuiltJsonGui.QuiltBasicButtonAction;
import org.quiltmc.loader.impl.gui.QuiltJsonGuiMessage.QuiltMessageListener;
import org.quiltmc.loader.impl.util.LoaderValueHelper;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class QuiltFork {

	private static final QuiltForkComms COMMS;
	private static final LoaderValueHelper<IOException> HELPER = LoaderValueHelper.IO_EXCEPTION;

	static {
		GameProvider provider = QuiltLoaderImpl.INSTANCE.getGameProvider();
		if (Boolean.getBoolean(SystemProperties.DISABLE_FORKED_GUIS) || !provider.canOpenGui()) {
			COMMS = null;
		} else {
			try {
				File base = QuiltLoaderImpl.INSTANCE.getQuiltLoaderCacheDir().resolve("comms").toFile();
				COMMS = QuiltForkComms.connect(base, QuiltFork::handleMessageFromServer);
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

	public static void openErrorGui(List<QuiltDisplayedError> errors) throws LoaderGuiException, LoaderGuiClosed {
		QuiltJsonGui tree = new QuiltJsonGui("Quilt Loader " + QuiltLoaderImpl.VERSION, "");
		tree.buttons.add(QuiltJsonButton.createUserSupportButton(tree));
		QuiltJsonButton continueButton = tree.addButton(QuiltLoaderText.of("button.ignore").toString(), QuiltBasicButtonAction.CONTINUE);
		continueButton.icon(QuiltLoaderGui.iconContinueIgnoring());
		for (QuiltDisplayedError error : errors) {
			QuiltJsonGuiMessage msg = (QuiltJsonGuiMessage) error;
			msg.listeners.add(new QuiltJsonGuiMessage.QuiltMessageListener() {
				@Override
				public void onFixed() {
					for (QuiltDisplayedError error : errors) {
						if (!((QuiltJsonGuiMessage) error).isFixed()) {
							return;
						}
					}
					continueButton.text(QuiltLoaderText.of("button.continue"));
					continueButton.icon(QuiltLoaderGui.iconContinue());
				}
			});
			tree.messages.add(msg);
		}
		openErrorGui(tree, true);
	}

	public static void openErrorGui(QuiltJsonGui tree, boolean shouldWait) throws LoaderGuiException, LoaderGuiClosed {
		if (COMMS == null) {
			// Gui disabled, act as if the user pressed "ignore"
			throw LoaderGuiClosed.INSTANCE;
		}

		tree.send();
		tree.open();

		if (shouldWait) {
			tree.waitUntilClosed();
		}
	}

	static void sendRaw(LoaderValue.LObject object) {
		if (COMMS != null) {
			COMMS.send(object);
		}
	}

	static void uploadIcon(int index, Map<Integer, BufferedImage> images) {
		Map<String, LoaderValue> map = new HashMap<>();
		map.put("__TYPE", lvf().string(ForkCommNames.ID_UPLOAD_ICON));
		map.put("index", lvf().number(index));
		Map<String, LoaderValue> imageMap = new HashMap<>();
		for (Map.Entry<Integer, BufferedImage> entry : images.entrySet()) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try {
				ImageIO.write(entry.getValue(), "png", baos);
			} catch (IOException e) {
				throw new Error("Failed to write image!", e);
			}
			String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
			imageMap.put(entry.getKey().toString(), lvf().string(base64));
		}
		map.put("images", lvf().object(imageMap));
		COMMS.send(lvf().object(map));
	}

	private static void handleMessageFromServer(LoaderValue msg) {
		try {
			handleMessageFromServer0(msg);
		} catch (IOException io) {
			String json = "<failed to turn sent json into a string>";
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
			LObject packet = msg.asObject();
			String type = HELPER.expectString(packet, "__TYPE");
			switch (type) {
				case ForkCommNames.ID_EXCEPTION: {
					// The server encountered an exception
					// We should really store the exception, but for now just exit
					COMMS.close();
					Log.error(LogCategory.COMMS, "The gui-server encountered an error!");
					System.exit(1);
					return;
				}
				case ForkCommNames.ID_GUI_OBJECT_UPDATE: {
					QuiltGuiSyncBase.updateObject(packet);
					return;
				}
			}
		}
		throw new Error("Unhandled message " + msg);
	}
}
