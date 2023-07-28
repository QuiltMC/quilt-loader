/*
 * Copyright 2022, 2023 QuiltMC
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

package org.quiltmc.loader.impl.util;

import java.util.HashMap;
import java.util.Map;

import org.quiltmc.loader.impl.filesystem.QuiltZipFileSystem;

/** Repetitive cleanup tasks, run every 10 seconds. Used to manage cleaning up {@link QuiltZipFileSystem}'s shared byte
 * channels, but might be used in the future for anything else which needs to perform cleanups. */
public class QuiltLoaderCleanupTasks {

	private static final Map<Object, Runnable> TASKS = new HashMap<>();
	private static int count;
	private static Thread thread;

	public static synchronized void addCleanupTask(Object key, Runnable cleaner) {
		TASKS.put(key, cleaner);
		initThread();
	}

	public static synchronized void removeCleanupTask(Object key) {
		TASKS.remove(key);
	}

	private static void initThread() {
		if (thread == null) {
			thread = new Thread(QuiltLoaderCleanupTasks::runTasks, "QuiltLoaderCleaner" + count++);
			thread.setDaemon(true);
			thread.start();
		}
	}

	private static void runTasks() {
		try {
			while (true) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// Ignored
				}

				Runnable[] tasks;
				synchronized (QuiltLoaderCleanupTasks.class) {
					tasks = TASKS.values().toArray(new Runnable[0]);
				}

				for (Runnable r : tasks) {
					try {
						r.run();
					} catch (Throwable t) {
						// Ignored
					}
				}

				synchronized (QuiltLoaderCleanupTasks.class) {
					if (TASKS.isEmpty()) {
						thread = null;
						return;
					}
				}
			}
		} finally {
			thread = null;
		}
	}
}
