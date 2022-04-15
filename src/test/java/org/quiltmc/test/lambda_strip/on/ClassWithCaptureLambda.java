package org.quiltmc.test.lambda_strip.on;

import org.quiltmc.loader.api.minecraft.ClientOnly;

public class ClassWithCaptureLambda {

	@ClientOnly
	public static void sayHello() {
		String message = "hi";
		message += " there";
		final String l = message;
		run(() -> System.out.println(l));
	}

	public static void run(Runnable task) {
		task.run();
	}
}
