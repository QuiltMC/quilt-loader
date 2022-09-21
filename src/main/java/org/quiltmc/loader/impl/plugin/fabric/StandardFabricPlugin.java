package org.quiltmc.loader.impl.plugin.fabric;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.plugin.QuiltPluginError;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode.SortOrder;
import org.quiltmc.loader.api.plugin.gui.Text;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.impl.metadata.FabricLoaderModMetadata;
import org.quiltmc.loader.impl.metadata.FabricModMetadataReader;
import org.quiltmc.loader.impl.metadata.NestedJarEntry;
import org.quiltmc.loader.impl.metadata.ParseMetadataException;
import org.quiltmc.loader.impl.plugin.BuiltinQuiltPlugin;

public class StandardFabricPlugin extends BuiltinQuiltPlugin {

	@Override
	public ModLoadOption[] scanZip(Path root, boolean fromClasspath, PluginGuiTreeNode guiNode) throws IOException {

		Path parent = context().manager().getParent(root);

		if (!parent.getFileName().toString().endsWith(".jar")) {
			return null;
		}

		return scan0(root, fromClasspath, guiNode);
	}

	@Override
	public ModLoadOption[] scanClasspathFolder(Path folder, PluginGuiTreeNode guiNode) throws IOException {
		return scan0(folder, true, guiNode);
	}

	private ModLoadOption[] scan0(Path root, boolean fromClasspath, PluginGuiTreeNode guiNode) throws IOException {
		Path fmj = root.resolve("fabric.mod.json");
		if (!Files.isRegularFile(fmj)) {
			return null;
		}

		try {
			FabricLoaderModMetadata meta = FabricModMetadataReader.parseMetadata(fmj);

			Path from = root;
			if (!fromClasspath) {
				from = context().manager().getParent(root);
			}

			jars: for (NestedJarEntry jarEntry : meta.getJars()) {
				String jar = jarEntry.getFile();
				Path inner = root;
				for (String part : jar.split("/")) {
					if ("..".equals(part)) {
						continue jars;
					}
					inner = inner.resolve(part);
				}

				if (inner == from) {
					continue;
				}

				PluginGuiTreeNode jarNode = guiNode.addChild(Text.of(jar), SortOrder.ALPHABETICAL_ORDER);
				context().addFileToScan(inner, jarNode);
			}

			boolean mandatory = fromClasspath || from.getFileSystem() == FileSystems.getDefault();
			// a mod needs to be remapped if we are in a development environment, and the mod
			// did not come from the classpath
			boolean requiresRemap = !fromClasspath && QuiltLoader.isDevelopmentEnvironment();
			return new ModLoadOption[] { new FabricModOption(context(), meta, from, root, mandatory, requiresRemap) };
		} catch (ParseMetadataException parse) {
			Text title = Text.translate("gui.text.invalid_metadata.title", "fabric.mod.json", parse.getMessage());
			QuiltPluginError error = context().reportError(title);
			String describedPath = context().manager().describePath(fmj);
			error.appendReportText("Invalid 'quilt.mod.json' metadata file:" + describedPath);
			error.appendDescription(Text.translate("gui.text.invalid_metadata.desc.0", describedPath));
			error.appendThrowable(parse);
			error.addFileViewButton(Text.translate("gui.view_file"), context().manager().getRealContainingFile(root));

			guiNode.addChild(Text.translate("gui.text.invalid_metadata", parse.getMessage()))//TODO: translate
				.setError(parse, error);
			return null;
		}
	}
}
