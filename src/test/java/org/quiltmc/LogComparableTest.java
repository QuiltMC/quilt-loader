/*
 * Copyright 2026 QuiltMC
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

package org.quiltmc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.junit.platform.commons.util.Preconditions;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.log.ConsoleLogHandler;
import org.quiltmc.loader.impl.util.log.Log;


public abstract class LogComparableTest {
	private static Path logLocation;

	@BeforeAll
	public static void setupLog() {
		System.setProperty(SystemProperties.LOG_LEVEL, "TRACE");
		logLocation = new File(System.getProperty("user.dir")).toPath()//
				.resolve("src")//
				.resolve("test")//
				.resolve("resources")//
				.resolve("testing")//
				.resolve("logs");
	}

	private PrintStream systemOut, systemErr;
	private ByteArrayOutputStream output;

	@BeforeEach
	public void beforeEach(TestInfo info) {
		if (shouldSkipTest(info)) return;

		systemOut = System.out;
		output = new ByteArrayOutputStream();
		System.setOut(new PrintStream(output));

		systemErr = System.err;
		System.setErr(new PrintStream(output));

		Log.init(new ConsoleLogHandler(), false);
		Log.disableBuiltinFormatting();
	}

	@AfterEach
	public void afterEach(TestInfo info) throws IOException {
		if (shouldSkipTest(info)) return;

		System.out.close();
		System.setOut(systemOut);
		System.err.close();
		System.setErr(systemErr);

		String rawResult = output.toString().trim();
		List<String> result;
		if (rawResult.isEmpty()) {
			result = Collections.emptyList();
		} else {
			result = Arrays.asList(rawResult.split("\r?\n"));
		}

		String displayName = info
				.getDisplayName()
				.replaceAll("\\[\\d+] |\\(\\)", "");
		String methodName = info.getTestMethod().get().getName();

		String fileName;
		if (methodName.equals(displayName)) {
			fileName = String.format("%s.log", methodName);
		} else {
			fileName = String.format("%s-%s.log", methodName, displayName);
		}

		Path outputFile = logLocation
				.resolve(info.getTestClass().get().getCanonicalName())
				.resolve(fileName);

		if (!Files.exists(outputFile)) {
			if (!result.isEmpty()) {
				Files.createDirectories(outputFile.getParent());
				Files.write(outputFile, result);
			}
		} else {
			List<String> original = Files.readAllLines(outputFile, StandardCharsets.UTF_8);
			Assertions.assertEquals(String.join("\n", original), String.join("\n", result));
		}
	}

	private boolean shouldSkipTest(TestInfo info) {
		if (!(info instanceof DynamicTestInfo)) {
			Annotation[] declaredAnnotations = info.getTestMethod().get().getDeclaredAnnotations();

			for (Annotation annotation : declaredAnnotations) {
				if (annotation.annotationType().equals(TestFactory.class)) {
					return true;
				}
			}
		}
		return false;
	}

	public <T> ThrowingConsumer<T> wrap(ThrowingConsumer<T> original, Method method, String name) {
		return t -> {
			TestInfo info = new DynamicTestInfo(name, method);

			try {
				this.beforeEach(info);
				original.accept(t);
			} finally {
				this.afterEach(info);
			}
		};
	}

	public <T> Stream<DynamicTest> streamDynamic(Stream<T> inputStream, Function<? super T, String> displayNameGenerator, ThrowingConsumer<? super T> testExecutor) {

		Preconditions.notNull(inputStream, "inputStream must not be null");
		Preconditions.notNull(displayNameGenerator, "displayNameGenerator must not be null");
		Preconditions.notNull(testExecutor, "testExecutor must not be null");

		Method method;
		try {
			StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
			StackTraceElement stackTraceElement = stackTrace[2];
			method = Class.forName(stackTraceElement.getClassName()).getDeclaredMethod(stackTraceElement.getMethodName());
		} catch (Exception e) {
			throw new RuntimeException("Unable to figure out calling method", e);
		}

		return inputStream
				.map(
						input -> DynamicTest.dynamicTest(
								displayNameGenerator.apply(input),
								() -> wrap(testExecutor, method, displayNameGenerator.apply(input)).accept(input)
						)
				);
	}

	private static class DynamicTestInfo implements TestInfo {
		private final String name;
		private final Method method;

		public DynamicTestInfo(String name, Method method) {
			this.name = name;
			this.method = method;
		}

		@Override
		public String getDisplayName() {
			return name;
		}

		@Override
		public Set<String> getTags() {
			return Collections.emptySet();
		}

		@Override
		public Optional<Class<?>> getTestClass() {
			return Optional.of(this.getClass());
		}

		@Override
		public Optional<Method> getTestMethod() {
			return Optional.of(method);
		}
	}
}
