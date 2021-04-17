/*
 * Copyright 2016 FabricMC
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

import java.awt.GraphicsEnvironment;
import java.util.HashSet;
import java.util.Set;

import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.game.GameProvider;
import org.quiltmc.loader.impl.gui.QuiltStatusTree.QuiltStatusNode;
import org.quiltmc.loader.impl.gui.QuiltStatusTree.QuiltStatusTab;

/** The main entry point for all quilt-based stuff. */
public final class QuiltGuiEntry {
	/** Opens the given {@link QuiltStatusTree} in a new swing window.
	 * 
	 * @throws Exception if something went wrong while opening the window. */
	public static void open(QuiltStatusTree tree) throws Exception {
		openWindow(tree, true);
	}

	private static void openWindow(QuiltStatusTree tree, boolean shouldWait) throws Exception {
		QuiltMainWindow.open(tree, shouldWait);
	}

	/** @param exitAfter If true then this will call {@link System#exit(int)} after showing the gui, otherwise this will
	 *            return normally. */
	public static void displayCriticalError(Throwable exception, boolean exitAfter) {
		QuiltLoaderImpl.INSTANCE.getLogger().fatal("A critical error occurred", exception);

		GameProvider provider = QuiltLoaderImpl.INSTANCE.getGameProvider();

		if ((provider == null || provider.canOpenErrorGui()) && !GraphicsEnvironment.isHeadless()) {
			QuiltStatusTree tree = new QuiltStatusTree();
			QuiltStatusTab crashTab = tree.addTab("Crash");

			tree.mainText = "Failed to launch!";
			addThrowable(crashTab.node, exception, new HashSet<>());

			// Maybe add an "open mods folder" button?
			// or should that be part of the main tree's right-click menu?
			tree.addButton("Exit").makeClose();

			try {
				open(tree);
			} catch (Exception e) {
				if (exitAfter) {
					QuiltLoaderImpl.INSTANCE.getLogger().warn("Failed to open the error gui!", e);
				} else {
					throw new RuntimeException("Failed to open the error gui!", e);
				}
			}
		}

		if (exitAfter) {
			System.exit(1);
		}
	}

	private static void addThrowable(QuiltStatusNode node, Throwable e, Set<Throwable> seen) {
		if (!seen.add(e)) {
			return;
		}

		// Remove some self-repeating exception traces from the tree
		// (for example the RuntimeException that is is created unnecessarily by ForkJoinTask)
		Throwable cause;

		while ((cause = e.getCause()) != null) {
			if (e.getSuppressed().length > 0) {
				break;
			}

			String msg = e.getMessage();

			if (msg == null) {
				msg = e.getClass().getName();
			}

			if (!msg.equals(cause.getMessage()) && !msg.equals(cause.toString())) {
				break;
			}

			e = cause;
		}

		QuiltStatusNode sub = node.addException(e);

		if (e.getCause() != null) {
			addThrowable(sub, e.getCause(), seen);
		}

		for (Throwable t : e.getSuppressed()) {
			addThrowable(sub, t, seen);
		}
	}
}
