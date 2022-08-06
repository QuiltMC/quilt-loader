package org.quiltmc.loader.impl.plugin.base;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.quiltmc.loader.api.plugin.ModContainerExt;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;

public abstract class InternalModContainerBase implements ModContainerExt {

	private final String pluginId;
	private final ModMetadataExt metadata;
	private final Path resourceRoot;
	private final List<List<Path>> sourcePaths;

	public InternalModContainerBase(QuiltPluginContext pluginContext, ModMetadataExt metadata, Path from, Path resourceRoot) {
		this.pluginId = pluginContext.pluginId();
		this.metadata = metadata;
		this.resourceRoot = resourceRoot;

		sourcePaths = walkSourcePaths(pluginContext, from);
	}

	private static List<List<Path>> walkSourcePaths(QuiltPluginContext pluginContext, Path from) {

		if (from.getFileSystem() == FileSystems.getDefault()) {
			return Collections.singletonList(Collections.singletonList(from));
		}

		Path fromRoot = from.getFileSystem().getPath("/");
		Collection<Path> joinedPaths = pluginContext.manager().getJoinedPaths(fromRoot);

		if (joinedPaths != null) {
			// TODO: Test joined paths!
			List<List<Path>> paths = new ArrayList<>();
			for (Path path : joinedPaths) {
				for (List<Path> upper : walkSourcePaths(pluginContext, path)) {
					List<Path> fullList = new ArrayList<>();
					fullList.addAll(upper);
					fullList.add(from);
					paths.add(Collections.unmodifiableList(fullList));
				}
			}
			return Collections.unmodifiableList(paths);
		}

		Path parent = pluginContext.manager().getParent(fromRoot);
		if (parent == null) {
			// That's not good
			return Collections.singletonList(Collections.singletonList(from));
		} else {
			List<List<Path>> paths = new ArrayList<>();
			for (List<Path> upper : walkSourcePaths(pluginContext, parent)) {
				List<Path> fullList = new ArrayList<>();
				fullList.addAll(upper);
				fullList.add(from);
				paths.add(Collections.unmodifiableList(fullList));
			}
			return Collections.unmodifiableList(paths);
		}
	}

	@Override
	public ModMetadataExt metadata() {
		return metadata;
	}

	@Override
	public Path rootPath() {
		return resourceRoot;
	}

	@Override
	public List<List<Path>> getSourcePaths() {
		return sourcePaths;
	}
}
