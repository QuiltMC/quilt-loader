package org.quiltmc.loader.impl.metadata.qmj;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;
import org.quiltmc.loader.impl.fabric.metadata.FabricModMetadataReader;
import org.quiltmc.loader.impl.fabric.metadata.ParseMetadataException;
import org.quiltmc.loader.impl.metadata.FabricLoaderModMetadata;

class InternalModMetadataJsonWriterTest {

	@Test
	void toJson() throws IOException, ParseMetadataException {
		FabricLoaderModMetadata read = FabricModMetadataReader.parseMetadata(new File(System.getProperty("user.dir")).toPath()
				.resolve("src")
				.resolve("test")
				.resolve("resources")
				.resolve("testing")
				.resolve("parsing")
				.resolve("fabric")
				.resolve("spec")
				.resolve("long.json"));


		StringWriter writer = new StringWriter();
		InternalModMetadataJsonWriter.write(((InternalModMetadata) read.asQuiltModMetadata()), writer);
		System.out.println(writer);
	}
}