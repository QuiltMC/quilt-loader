package org.quiltmc.loader.impl;

import java.util.LinkedHashMap;

public class __MEMORY {
	public static final LinkedHashMap<String, Double> SNAPSHOTS = new LinkedHashMap<>();

	static {
		Thread gc = new Thread("GC invoker") {
			@Override
			public void run() {
				while (true) {
					try {
						Thread.sleep(20);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					System.gc();
				}
			}
		};
//		gc.setDaemon(true);
		gc.start();
	}

	public static void mem(String name) {
		System.out.println("__MEMORY " + name);
		for (int i = 0; i < 3; i++) {
			System.gc();
			System.gc();
			System.gc();
			System.gc();
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		long free = Runtime.getRuntime().freeMemory();
		long max = Runtime.getRuntime().maxMemory();
		SNAPSHOTS.put(name, (max - free) / 1024 / 1000.0);
	}
}
