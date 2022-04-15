package org.quiltmc.test.lambda_strip.on;

import org.quiltmc.loader.api.minecraft.ClientOnly;

public class ClassWithMethodReference {

	@ClientOnly
	public static void main(String[] args) {
		run(ClassWithMethodReference::sayHello);
	}

	public static void sayHello() {
		System.out.println("Hello");
	}

	public static void run(Runnable task) {
		task.run();
	}
}
