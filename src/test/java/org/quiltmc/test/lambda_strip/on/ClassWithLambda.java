package org.quiltmc.test.lambda_strip.on;

import org.quiltmc.loader.api.minecraft.ClientOnly;
import org.quiltmc.loader.api.minecraft.ClientOnlyInterface;

@ClientOnlyInterface(ClientItf.class)
public class ClassWithLambda implements ClientItf {

	@ClientOnly
	public static void sayHello() {
		run(() -> System.out.println("Hello"));
	}

	public static void run(Runnable task) {
		task.run();
	}

	@Override
	@ClientOnly
	public void sayHi() {
		sayHello();
	}
}
