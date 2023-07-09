/*
 * Copyright 2022, 2023 QuiltMC
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

package org.quiltmc.loader.impl.plugin.quilt;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.regex.Pattern;

import org.quiltmc.json5.exception.ParseException;
import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.ModMetadata.ProvidedMod;
import org.quiltmc.loader.api.gui.LoaderGuiClosed;
import org.quiltmc.loader.api.gui.LoaderGuiException;
import org.quiltmc.loader.api.gui.QuiltDisplayedError;
import org.quiltmc.loader.api.gui.QuiltLoaderGui;
import org.quiltmc.loader.api.gui.QuiltLoaderIcon;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.VersionRange;
import org.quiltmc.loader.api.plugin.ModLocation;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.QuiltPluginManager;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode.SortOrder;
import org.quiltmc.loader.api.plugin.solver.AliasedLoadOption;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.RuleContext;
import org.quiltmc.loader.impl.filesystem.QuiltJoinedFileSystem;
import org.quiltmc.loader.impl.game.GameProvider;
import org.quiltmc.loader.impl.game.GameProvider.BuiltinMod;
import org.quiltmc.loader.impl.metadata.qmj.InternalModMetadata;
import org.quiltmc.loader.impl.metadata.qmj.ModMetadataReader;
import org.quiltmc.loader.impl.metadata.qmj.QuiltOverrides;
import org.quiltmc.loader.impl.metadata.qmj.QuiltOverrides.ModOverrides;
import org.quiltmc.loader.impl.metadata.qmj.QuiltOverrides.SpecificOverrides;
import org.quiltmc.loader.impl.metadata.qmj.V1ModMetadataBuilder;
import org.quiltmc.loader.impl.plugin.BuiltinQuiltPlugin;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

/** Quilt-loader's plugin. For simplicities sake this is a builtin plugin - and cannot be disabled, or reloaded (since
 * quilt-loader can't reload itself to a different version). */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class StandardQuiltPlugin extends BuiltinQuiltPlugin {

	public static final boolean DEBUG_PRINT_STATE = Boolean.getBoolean(SystemProperties.DEBUG_MOD_SOLVING);

	private QuiltOverrides overrides;
	private final Map<String, OptionalModIdDefintion> modDefinitions = new HashMap<>();

	@Override
	public void load(QuiltPluginContext context, Map<String, LoaderValue> previousData) {
		super.load(context, previousData);
		loadOverrides();
	}

	private void loadOverrides() {
		Path overrideFile = context().manager().getConfigDirectory().resolve("quilt-loader-overrides.json");
		try {
			overrides = new QuiltOverrides(overrideFile);
		} catch (ParseException | IOException e) {
			Exception[] mostRecentException = { e };
			QuiltLoaderText title = QuiltLoaderText.translate("error.quilt_overrides.io_parse.title");
			QuiltDisplayedError error = QuiltLoaderGui.createError(title);
			error.appendDescription(QuiltLoaderText.of(e.getMessage()));
			error.appendThrowable(e);
			error.addFileViewButton(QuiltLoaderText.translate("button.view_file", "config/"), overrideFile).icon(QuiltLoaderGui.iconFolder());
			error.addFileEditButton(overrideFile).icon(QuiltLoaderGui.iconJsonFile());
			error.addActionButton(QuiltLoaderText.translate("button.reload"), () -> {
				try {
					overrides = new QuiltOverrides(overrideFile);
					error.setFixed();
				} catch (ParseException | IOException e2) {
					mostRecentException[0] = e2;
					e2.printStackTrace();
					error.clearDescription();
					error.appendDescription(QuiltLoaderText.of(e2.getMessage()));
				}
			}).icon(QuiltLoaderGui.iconReload());

			try {
				QuiltLoaderGui.openErrorGui(error);
				return;
			} catch (LoaderGuiException ex) {
				mostRecentException[0].addSuppressed(ex);
			} catch (LoaderGuiClosed closed) {
				// Either closed correctly or ignored
			}

			if (overrides == null) {
				Exception ex = mostRecentException[0];
				QuiltDisplayedError error2 = context().reportError(title);
				error2.appendDescription(QuiltLoaderText.of(ex.getMessage()));
				error2.addFileViewButton(overrideFile);
				error2.appendReportText("Failed to read the quilt-loader-overrides.json file!");
				error2.appendThrowable(ex);
				context().haltLoading();
			}
		}
	}

	public boolean hasDepsChanged(ModLoadOption mod) {
		ModOverrides modOverrides = overrides.pathOverrides.get(context().manager().describePath(mod.from()));
		return modOverrides != null && modOverrides.hasDepsChanged();
	}

	public boolean hasDepsRemoved(ModLoadOption mod) {
		ModOverrides modOverrides = overrides.pathOverrides.get(context().manager().describePath(mod.from()));
		return modOverrides != null && modOverrides.hasDepsRemoved();
	}

	public void addBuiltinMods(GameProvider game) {
		int gameIndex = 1;
		for (BuiltinMod mod : game.getBuiltinMods()) {
			addBuiltinMod(mod, "game-" + gameIndex);
			gameIndex++;
		}

		String javaVersion = System.getProperty("java.specification.version").replaceFirst("^1\\.", "");
		V1ModMetadataBuilder javaMeta = new V1ModMetadataBuilder();
		javaMeta.id = "java";
		javaMeta.group = "builtin";
		javaMeta.version = Version.of(javaVersion);
		javaMeta.name = System.getProperty("java.vm.name");
		Path javaPath = new File(System.getProperty("java.home")).toPath();
		addSystemMod(new BuiltinMod(Collections.singletonList(javaPath), javaMeta.build()), "java");
	}

	private void addSystemMod(BuiltinMod mod, String name) {
		addInternalMod(mod, name, true);
	}

	private void addBuiltinMod(BuiltinMod mod, String name) {
		addInternalMod(mod, name, false);
	}

	private void addInternalMod(BuiltinMod mod, String name, boolean system) {

		boolean changed = false;
		List<Path> openedPaths = new ArrayList<>();

		for (Path from : mod.paths) {

			Path inside = null;

			if (from.getFileName().toString().endsWith(".jar")) {
				try {
					inside = FileSystems.newFileSystem(from, (ClassLoader) null).getPath("/");
				} catch (IOException e) {
					// A bit odd, but not necessarily a crash-worthy issue
					e.printStackTrace();
				}
			}

			if (inside == null) {
				openedPaths.add(from);
			} else {
				changed = true;
				openedPaths.add(inside);
			}
		}

		Path from = join(mod.paths, name);
		Path inside = changed ? join(openedPaths, name) : from;

		// We don't go via context().addModOption since we don't really have a good gui node to base it off
		context().ruleContext().addOption(
			system //
				? new SystemModOption(context(), mod.metadata, from, inside) //
				: new BuiltinModOption(context(), mod.metadata, from, inside)
		);
	}

	private static Path join(List<Path> paths, String name) {
		if (paths.size() == 1) {
			return paths.get(0);
		} else {
			return new QuiltJoinedFileSystem(name, paths).getRoot();
		}
	}

	@Override
	public ModLoadOption[] scanZip(Path root, ModLocation location, PluginGuiTreeNode guiNode) throws IOException {

		Path parent = context().manager().getParent(root);

		if (!parent.getFileName().toString().endsWith(".jar")) {
			System.out.println("Found file " + parent);
			return null;
		}

		return scan0(root, QuiltLoaderGui.iconJarFile(), location, true, guiNode);
	}

	@Override
	public ModLoadOption[] scanFolder(Path folder, ModLocation location, PluginGuiTreeNode guiNode) throws IOException {
		return scan0(folder, QuiltLoaderGui.iconFolder(), location, false, guiNode);
	}

	@Override
	public ModLoadOption[] scanUnknownFile(Path file, ModLocation location, PluginGuiTreeNode guiNode)
		throws IOException {
		System.out.println("Found file " + file);
		return null;
	}

	private ModLoadOption[] scan0(Path root, QuiltLoaderIcon fileIcon, ModLocation location, boolean isZip,
		PluginGuiTreeNode guiNode) throws IOException {

		Path qmj = root.resolve("quilt.mod.json");
		if (!FasterFiles.isRegularFile(qmj)) {
			return null;
		}

		try {
			InternalModMetadata meta = ModMetadataReader.read(qmj, context().manager(), guiNode);

			Path from = root;
			if (isZip) {
				from = context().manager().getParent(root);
			}

			jars: for (String jar : meta.jars()) {
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

				PluginGuiTreeNode jarNode = guiNode.addChild(QuiltLoaderText.of(jar), SortOrder.ALPHABETICAL_ORDER);
				context().addFileToScan(inner, jarNode, false);
			}

			// a mod needs to be remapped if we are in a development environment, and the mod
			// did not come from the classpath
			boolean requiresRemap = !location.onClasspath() && QuiltLoader.isDevelopmentEnvironment();
			return new ModLoadOption[] { new QuiltModOption(
				context(), meta, from, fileIcon, root, location.isDirect(), requiresRemap
			) };
		} catch (ParseException parse) {
			QuiltLoaderText title = QuiltLoaderText.translate(
				"gui.text.invalid_metadata.title", "quilt.mod.json", parse.getMessage()
			);
			QuiltDisplayedError error = context().reportError(title);
			String describedPath = context().manager().describePath(qmj);
			error.appendReportText("Invalid 'quilt.mod.json' metadata file:" + describedPath);
			error.appendDescription(QuiltLoaderText.translate("gui.text.invalid_metadata.desc.0", describedPath));
			error.appendThrowable(parse);
			context().manager().getRealContainingFile(root).ifPresent(real ->
					error.addFileViewButton(real)
							.icon(QuiltLoaderGui.iconJarFile().withDecoration(QuiltLoaderGui.iconQuilt()))
			);

			guiNode.addChild(QuiltLoaderText.translate("gui.text.invalid_metadata", parse.getMessage()))//
				.setError(parse, error);
			return null;
		}
	}

	@Override
	public void onLoadOptionAdded(LoadOption option) {

		// We handle dependency solving for all plugins that don't tell us not to.

		if (option instanceof AliasedLoadOption) {
			AliasedLoadOption alias = (AliasedLoadOption) option;
			if (alias.getTarget() != null) {
				return;
			}
		}

		if (option instanceof ModLoadOption) {
			ModLoadOption mod = (ModLoadOption) option;
			ModMetadataExt metadata = mod.metadata();
			RuleContext ctx = context().ruleContext();

			OptionalModIdDefintion def = modDefinitions.get(mod.id());
			if (def == null) {
				def = new OptionalModIdDefintion(context().manager(), ctx, mod.id());
				modDefinitions.put(mod.id(), def);
				ctx.addRule(def);
			}

			// TODO: this minecraft-specific extension should be moved to its own plugin
			// If the mod's environment doesn't match the current one,
			// then add a rule so that the mod is never loaded.
			if (!metadata.environment().matches(context().manager().getEnvironment())) {
				ctx.addRule(new DisabledModIdDefinition(context(), mod));
			} else if (mod.isMandatory()) {
				ctx.addRule(new MandatoryModIdDefinition(context(), mod));
			}

			if (metadata.shouldQuiltDefineProvides()) {
				Collection<? extends ProvidedMod> provides = metadata.provides();

				for (ProvidedMod provided : provides) {
					PluginGuiTreeNode guiNode = context().manager().getGuiNode(mod)//
						.addChild(QuiltLoaderText.translate("gui.text.providing", provided.id()));
					guiNode.mainIcon(QuiltLoaderGui.iconUnknownFile());
					context().addModLoadOption(new ProvidedModOption(mod, provided), guiNode);
				}
			}

			if (metadata.shouldQuiltDefineDependencies()) {

				Path path = mod.from();
				String described = context().manager().describePath(path);
				if (Boolean.getBoolean(SystemProperties.DEBUG_DUMP_OVERRIDE_PATHS)) {
					Log.info(LogCategory.DISCOVERY, "Override path: '" + described + "'");
				}

				Collection<ModDependency> depends = metadata.depends();
				Collection<ModDependency> breaks = metadata.breaks();

				List<SingleOverrideEntry> overrideList = new ArrayList<>();
				ModOverrides byPath = overrides.pathOverrides.get(described);
				if (byPath != null) {
					overrideList.add(new SingleOverrideEntry(byPath, true));
				}

				for (Entry<Pattern, ModOverrides> entry : overrides.patternOverrides.entrySet()) {
					if (!entry.getKey().matcher(mod.id()).matches()) {
						continue;
					}

					overrideList.add(new SingleOverrideEntry(entry.getValue(), false));
				}

				depends = new HashSet<>(depends);
				breaks = new HashSet<>(breaks);

				for (SingleOverrideEntry override : overrideList) {
					replace(override.fuzzy, override.overrides.dependsOverrides, depends);
					replace(override.fuzzy, override.overrides.breakOverrides, breaks);
				}

				for (ModDependency dep : depends) {
					if (!dep.shouldIgnore()) {
						ctx.addRule(createModDepLink(context().manager(), ctx, mod, dep));
					}
				}

				for (ModDependency dep : breaks) {
					if (!dep.shouldIgnore()) {
						ctx.addRule(createModBreaks(context().manager(), ctx, mod, dep));
					}
				}
			}
		}
	}

	private static void warn(String msg) {
		Log.warn(LogCategory.DISCOVERY, "'" + msg);
	}

	static final class SingleOverrideEntry {
		final ModOverrides overrides;
		final boolean fuzzy;

		public SingleOverrideEntry(ModOverrides overrides, boolean fuzzy) {
			this.overrides = overrides;
			this.fuzzy = fuzzy;
		}
	}

	private static void replace(boolean fuzzy, SpecificOverrides overrides, Collection<ModDependency> in) {
		for (Map.Entry<ModDependency, ModDependency> entry : overrides.replacements.entrySet()) {
			if (remove(fuzzy, in, entry.getKey(), "replace")) {
				in.add(entry.getValue());
			}
		}

		for (ModDependency removal : overrides.removals) {
			remove(fuzzy, in, removal, "remove");
		}

		in.addAll(overrides.additions);
	}

	private static boolean remove(boolean fuzzy, Collection<ModDependency> in, ModDependency removal, String name) {
		if (in.remove(removal)) {
			return true;
		}

		if (fuzzy && removal instanceof ModDependency.Only) {
			ModDependency.Only specific = (ModDependency.Only) removal;
			if (specific.versionRange() == VersionRange.ANY && specific.unless() == null) {
				List<ModDependency> matches = new ArrayList<>();
				for (ModDependency dep : in) {
					if (!(dep instanceof ModDependency.Only)) {
						continue;
					}
					ModDependency.Only current = (ModDependency.Only) dep;
					if (!current.id().equals(specific.id())) {
						continue;
					}
					matches.add(current);
				}

				if (matches.size() == 1) {
					in.remove(matches.get(0));
					return true;
				} else if (matches.size() > 1) {
					warn("Found multiple matching ModDependency " + name + " when using using fuzzy matching!");
					logModDep("", "", removal);
					warn("Comparison:");
					if (in.isEmpty()) {
						warn("  (None left)");
					}
					int index = 0;
					for (ModDependency with : in) {
						logCompare(" ", "[" + index++ + "]: ", removal, with);
					}
					return false;
				}
			}
		}

		warn("Failed to find the ModDependency 'from' to " + name + "!");
		logModDep("", "", removal);
		warn("Comparison:");
		if (in.isEmpty()) {
			warn("  (None left)");
		}
		int index = 0;
		for (ModDependency with : in) {
			logCompare(" ", "[" + index++ + "]: ", removal, with);
		}
		return false;
	}

	private static void logModDep(String indent, String firstPrefix, ModDependency value) {
		if (value instanceof ModDependency.Only) {
			ModDependency.Only only = (ModDependency.Only) value;
			warn(indent + firstPrefix + only.id() + " versions " + only.versionRange() + //
				(only.optional() ? "(optional)" : "(mandatory)"));
			if (only.unless() != null) {
				logModDep(indent + "  ", "unless ", value);
			}
		} else if (value instanceof ModDependency.All) {
			ModDependency.All all = (ModDependency.All) value;
			warn(indent + firstPrefix + " all of: ");
			for (ModDependency.Only on : all) {
				logModDep(indent + "  ", "", on);
			}
		} else {
			ModDependency.Any all = (ModDependency.Any) value;
			warn(indent + firstPrefix + " any of: ");
			for (ModDependency.Only on : all) {
				logModDep(indent + "  ", "", on);
			}
		}
	}

	private static void logCompare(String indent, String firstPrefix, ModDependency from, ModDependency with) {
		if (Objects.equals(from, with)) {
			warn(indent + firstPrefix + "matches!");
			return;
		}
		if (from instanceof ModDependency.Only) {
			if (with instanceof ModDependency.Only) {
				ModDependency.Only f = (ModDependency.Only) from;
				ModDependency.Only t = (ModDependency.Only) with;
				warn(indent + firstPrefix + "on:");
				logCompareValue(indent + "  id ", f.id(), t.id());
				logCompareValue(indent + "  versions ", f.versionRange(), t.versionRange());
				logCompareValue(indent + "  optional? ", f.optional(), t.optional());
				if (f.unless() == null && t.unless() == null) {
					logCompare(indent + "  ", "unless ", f.unless(), t.unless());
				}
			} else {
				warn(indent + firstPrefix + "'from' is a direct dependency, but 'with' is not.");
				logModDep(indent + "  ", "from: ", from);
			}
		} else if (from instanceof ModDependency.All) {
			if (with instanceof ModDependency.All) {
				ModDependency.All f = (ModDependency.All) from;
				ModDependency.All t = (ModDependency.All) with;
				warn(indent + firstPrefix + "all of:");
				logListCompare(indent, f, t);
			} else {
				warn(indent + firstPrefix + "'from' is an all-of dependency list, but 'with' is not.");
				logModDep(indent + "  ", "from: ", from);
			}
		} else if (from != null) {
			if (with instanceof ModDependency.Any) {
				ModDependency.Any f = (ModDependency.Any) from;
				ModDependency.Any t = (ModDependency.Any) with;
				warn(indent + firstPrefix + "any of:");
				logListCompare(indent, f, t);
			} else {
				warn(indent + firstPrefix + "'from' is an any-of dependency list, but 'with' is not.");
				logModDep(indent + "  ", "from: ", from);
			}
		} else {
			warn(indent + firstPrefix + "'from' is missing, but 'with' is not.");
		}
	}

	private static void logListCompare(String indent, Collection<ModDependency.Only> f, Collection<ModDependency.Only> t) {
		Iterator<ModDependency.Only> fromIter = f.iterator();
		Iterator<ModDependency.Only> withIter = t.iterator();
		int index = 0;
		while (true) {
			ModDependency.Only from = fromIter.hasNext() ? fromIter.next() : null;
			ModDependency.Only with = withIter.hasNext() ? withIter.next() : null;
			logCompare(indent + "  ", "[" + index++ + "]", from, with);
		}
	}

	private static void logCompareValue(String start, Object a, Object b) {
		warn(start + a + (a.equals(b) ? " == " : " != ") + b);
	}

	public static QuiltRuleDep createModDepLink(QuiltPluginManager manager, RuleContext ctx, LoadOption option,
		ModDependency dep) {

		if (dep instanceof ModDependency.Any) {
			ModDependency.Any any = (ModDependency.Any) dep;

			return new QuiltRuleDepAny(manager, ctx, option, any);
		} else {
			ModDependency.Only only = (ModDependency.Only) dep;

			return new QuiltRuleDepOnly(manager, ctx, option, only);
		}
	}

	public static QuiltRuleBreak createModBreaks(QuiltPluginManager manager, RuleContext ctx, LoadOption option,
		ModDependency dep) {
		if (dep instanceof ModDependency.All) {
			ModDependency.All any = (ModDependency.All) dep;

			return new QuiltRuleBreakAll(manager, ctx, option, any);
		} else {
			ModDependency.Only only = (ModDependency.Only) dep;

			return new QuiltRuleBreakOnly(manager, ctx, option, only);
		}
	}
}
