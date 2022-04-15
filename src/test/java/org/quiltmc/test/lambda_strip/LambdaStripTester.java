package org.quiltmc.test.lambda_strip;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.TraceClassVisitor;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.transformer.ClassStripper;
import org.quiltmc.loader.impl.transformer.EnvironmentStrippingData;
import org.quiltmc.loader.impl.transformer.LambdaStripCalculator;

import net.fabricmc.api.EnvType;

public class LambdaStripTester {

	// Not a real test, since I'm not quite sure how to check for "no lambda present"
	// Perhaps just "the class contains no additional unexpected methods"?

	public static void main(String[] args) throws IOException {
		String[] files = { //
			"bin/test/org/quiltmc/test/lambda_strip/on/ClassWithLambda.class", //
			"bin/test/org/quiltmc/test/lambda_strip/on/ClassWithCaptureLambda.class", //
			"bin/test/org/quiltmc/test/lambda_strip/on/ClassWithMethodReference.class"//
		};

		for (String path : files) {

			System.out.println("");
			System.out.println(
				"========================================================================================="
			);
			System.out.println("");

			System.out.println("ORIGINAL:");
			byte[] bytes = Files.readAllBytes(Paths.get(path));

			ClassReader reader = new ClassReader(bytes);
			reader.accept(
				new TraceClassVisitor(new PrintWriter(System.out, true)), ClassReader.SKIP_DEBUG
					| ClassReader.SKIP_FRAMES
			);

			EnvironmentStrippingData stripData = new EnvironmentStrippingData(Opcodes.ASM9, EnvType.SERVER);
			reader.accept(stripData, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);

			Collection<String> stripMethods = stripData.getStripMethods();

			LambdaStripCalculator calc = new LambdaStripCalculator(Opcodes.ASM9, stripData.getStripMethodLambdas());
			reader.accept(calc, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
			Collection<String> additionalStripMethods = calc.computeAdditionalMethodsToStrip();

			if (!additionalStripMethods.isEmpty()) {
				stripMethods = new HashSet<>(stripMethods);
				stripMethods.addAll(additionalStripMethods);
			}

			ClassWriter classWriter = new ClassWriter(null, 0);
			ClassStripper visitor = new ClassStripper(
				QuiltLoaderImpl.ASM_VERSION, classWriter, stripData.getStripInterfaces(), stripData.getStripFields(),
				stripMethods
			);
			reader.accept(visitor, 0);

			System.out.println("TRANSFORMED:");

			ClassReader r2 = new ClassReader(classWriter.toByteArray());
			r2.accept(
				new TraceClassVisitor(new PrintWriter(System.out, true)), ClassReader.SKIP_DEBUG
					| ClassReader.SKIP_FRAMES
			);
		}
	}
}
