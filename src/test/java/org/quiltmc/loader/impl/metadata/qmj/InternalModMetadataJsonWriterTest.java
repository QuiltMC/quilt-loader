/*
 * Copyright 2024 QuiltMC
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

package org.quiltmc.loader.impl.metadata.qmj;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.quiltmc.loader.impl.fabric.metadata.FabricModMetadataReader;
import org.quiltmc.loader.impl.fabric.metadata.ParseMetadataException;

class InternalModMetadataJsonWriterTest {

	static Stream<Path> fabricJsons() throws IOException {
		Path specJsons = new File(System.getProperty("user.dir")).toPath()
				.resolve("src/test/resources/testing/parsing/fabric/spec");

		return StreamSupport.stream(Files.newDirectoryStream(specJsons).spliterator(), false);
	}

	@ParameterizedTest()
	@MethodSource("org.quiltmc.loader.impl.metadata.qmj.InternalModMetadataJsonWriterTest#fabricJsons")
	void fabricToJson(Path path) throws IOException, ParseMetadataException {
		InternalModMetadata read = (InternalModMetadata) FabricModMetadataReader.parseMetadata(path).asQuiltModMetadata();

		StringWriter writer = new StringWriter();
		InternalModMetadataJsonWriter.write(read, writer);
		String output = writer.toString();
		System.out.println(output);

		JsonLoaderValue.ObjectImpl json = (JsonLoaderValue.ObjectImpl) JsonLoaderFactoryImpl.INSTANCE.read(new ByteArrayInputStream(output.getBytes()));
		V1ModMetadataImpl readQuilt = V1ModMetadataReader.read(json);
	}

	static Stream<Path> quiltJsons() throws IOException {
		Path specJsons = new File(System.getProperty("user.dir")).toPath()
				.resolve("src/test/resources/testing/parsing/quilt/v1/auto/spec");

		return StreamSupport.stream(Files.newDirectoryStream(specJsons).spliterator(), false);
	}

	@ParameterizedTest()
	@MethodSource("org.quiltmc.loader.impl.metadata.qmj.InternalModMetadataJsonWriterTest#quiltJsons")
	void quiltToJson(Path path) throws IOException {
		JsonLoaderValue.ObjectImpl json = (JsonLoaderValue.ObjectImpl) JsonLoaderFactoryImpl.INSTANCE.read(Files.newInputStream(path));
		InternalModMetadata read = V1ModMetadataReader.read(json).asQuiltModMetadata();

		StringWriter writer = new StringWriter();
		InternalModMetadataJsonWriter.write(read, writer);
		String output = writer.toString();
		System.out.println(output);

		json = (JsonLoaderValue.ObjectImpl) JsonLoaderFactoryImpl.INSTANCE.read(new ByteArrayInputStream(output.getBytes()));
		V1ModMetadataImpl readQuilt = V1ModMetadataReader.read(json);
	}
}