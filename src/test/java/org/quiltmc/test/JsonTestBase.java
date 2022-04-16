package org.quiltmc.test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.quiltmc.json5.JsonReader;

abstract class JsonTestBase {

	JsonReader get(String s) throws IOException {
		Path path;
		try {
			path = Paths.get(getClass().getClassLoader().getResource(s).toURI());
		} catch (URISyntaxException e) {
			throw new IOException(e);
		}
		return JsonReader.json(path);
	}
}
