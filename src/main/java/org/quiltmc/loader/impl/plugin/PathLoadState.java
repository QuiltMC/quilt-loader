package org.quiltmc.loader.impl.plugin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.gui.QuiltDisplayedError;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.plugin.QuiltLoaderPlugin;
import org.quiltmc.loader.api.plugin.QuiltPluginManager;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.impl.plugin.UnsupportedModChecker.UnsupportedModType;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
abstract class PathLoadState {

	final Path path;
	final ModLocationImpl location;
	UnsupportedModType unsupportedType = null;

	/** Map of plugin ID to list of {@link ModLoadOption} which was loaded by that plugin. This contains empty lists
	 * rather than null values if the plugin didn't load anything from the mod. */
	private final Map<String, List<ModLoadOption>> loadedByPlugin = new LinkedHashMap<>();

	private String currentHighestPriority;

	/** Caches the return value of {@link QuiltLoaderPlugin#isHigherPriorityThan(Path, List, String)} to ensure we don't
	 * call this multiple times. */
	private final Map<PluginPriorityComparison, Boolean> pluginPriorities = new HashMap<>();

	PathLoadState(Path path, ModLocationImpl location) {
		this.path = path;
		this.location = location;
	}

	void add(QuiltPluginManagerImpl manager, BasePluginContext plugin, List<ModLoadOption> mods) {
		String pluginId = plugin.pluginId;
		List<ModLoadOption> list = loadedByPlugin.computeIfAbsent(pluginId, p -> new ArrayList<>());
		list.addAll(mods);
		if (list.isEmpty()) {
			return;
		}

		if (currentHighestPriority == null) {
			currentHighestPriority = pluginId;
			for (ModLoadOption option : mods) {
				manager.addLoadOption(option, plugin);
			}
			return;
		}

		for (Entry<String, List<ModLoadOption>> entry : loadedByPlugin.entrySet()) {
			String other = entry.getKey();
			if (other.equals(pluginId)) {
				continue;
			}
			List<ModLoadOption> otherMods = entry.getValue();
			if (otherMods.isEmpty()) {
				continue;
			}

			if (!currentHighestPriority.equals(other)) {
				// No reason to compare priorities if it wouldn't change anything.
				continue;
			}

			PluginPriorityComparison thisToOther = new PluginPriorityComparison(pluginId, other);
			PluginPriorityComparison reverse = thisToOther.inverse();
			List<ModLoadOption> thisOptions = Collections.unmodifiableList(list);
			List<ModLoadOption> otherOptions = Collections.unmodifiableList(otherMods);
			Boolean thisValue = plugin.plugin().isHigherPriorityThan(path, thisOptions, other, otherOptions);
			pluginPriorities.put(thisToOther, thisValue);
			Boolean otherValue = pluginPriorities.computeIfAbsent(
				reverse, r -> manager.getPlugin(other).plugin()//
					.isHigherPriorityThan(path, otherOptions, pluginId, thisOptions)
			);

			final boolean isThisHigher;

			if (thisValue == null) {
				if (otherValue == null) {
					StringBuilder sb = new StringBuilder();
					sb.append("At least one plugin needs to implement 'QuiltLoaderPlugin.isHigherPriorityThan'!\n");
					sb.append("Plugin 1: " + pluginId + "\n");
					sb.append("Plugin 2: " + other + "\n");
					sb.append("Path: " + manager.describePath(path));
					throw new IllegalStateException(sb.toString());
				} else {
					isThisHigher = !otherValue;
				}
			} else if (otherValue == null) {
				isThisHigher = thisValue;
			} else if (!otherValue.equals(thisValue)) {
				isThisHigher = thisValue;
			} else {
				StringBuilder sb = new StringBuilder();
				sb.append("Found two plugins with conflicting 'QuiltLoaderPlugin.isHigherPriorityThan' results! (both returned ");
				sb.append(thisValue);
				sb.append(")\n");
				sb.append("Plugin 1: " + pluginId + "\n");
				sb.append("Plugin 2: " + other + "\n");
				sb.append("Path: " + manager.describePath(path));
				throw new IllegalStateException(sb.toString());
			}

			if (isThisHigher) {
				currentHighestPriority = pluginId;

				for (ModLoadOption option : otherMods) {
					manager.removeLoadOption(option);
				}

				for (ModLoadOption option : list) {
					manager.addLoadOption(option, plugin);
				}
			}
		}
	}

	Set<String> getPlugins() {
		return loadedByPlugin.keySet();
	}

	List<ModLoadOption> getLoadedBy(String pluginId) {
		return loadedByPlugin.get(pluginId);
	}

	@Nullable ModLoadOption getCurrentModOption() {
		if (currentHighestPriority == null) {
			return null;
		}
		List<ModLoadOption> list = loadedByPlugin.get(currentHighestPriority);
		return list == null ? null : list.iterator().next();
	}

	Map<String, List<ModLoadOption>> getMap() {
		Map<String, List<ModLoadOption>> map = new HashMap<>();
		for (Map.Entry<String, List<ModLoadOption>> entry : loadedByPlugin.entrySet()) {
			List<ModLoadOption> list = entry.getValue();
			if (!list.isEmpty()) {
				map.put(entry.getKey(), Collections.unmodifiableList(list));
			}
		}
		return Collections.unmodifiableMap(map);
	}

	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	static final class PluginPriorityComparison {
		final String pluginFrom;
		final String pluginTo;

		PluginPriorityComparison(String pluginFrom, String pluginTo) {
			this.pluginFrom = pluginFrom;
			this.pluginTo = pluginTo;
		}

		PluginPriorityComparison inverse() {
			return new PluginPriorityComparison(pluginTo, pluginFrom);
		}

		@Override
		public int hashCode() {
			return pluginFrom.hashCode() * 31 + pluginTo.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) return true;
			if (obj == null) return false;
			if (obj.getClass() != getClass()) {
				return false;
			}
			PluginPriorityComparison other = (PluginPriorityComparison) obj;
			return pluginFrom.equals(other.pluginFrom) && pluginTo.equals(other.pluginTo);
		}
	}

	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	static final class ExtraMod extends PathLoadState {
		ExtraMod(Path path) {
			super(path, null);
		}
	}

	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	static final class UnknownFile extends PathLoadState {
		UnknownFile(Path path, ModLocationImpl location) {
			super(path, location);
		}
	}

	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	static final class Folder extends PathLoadState {
		Folder(Path path, ModLocationImpl location) {
			super(path, location);
		}
	}

	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	static final class Zip extends PathLoadState {
		final Path insideZipRoot;

		public Zip(Path path, ModLocationImpl location, Path insideZipRoot) {
			super(path, location);
			this.insideZipRoot = insideZipRoot;
		}
	}
}
