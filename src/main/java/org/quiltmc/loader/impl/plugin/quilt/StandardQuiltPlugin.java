package org.quiltmc.loader.impl.plugin.quilt;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.quiltmc.json5.exception.ParseException;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.api.plugin.ModMetadataExt.ProvidedMod;
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
import org.quiltmc.loader.impl.metadata.qmj.V1ModMetadataBuilder;
import org.quiltmc.loader.impl.plugin.BuiltinQuiltPlugin;

/** Quilt-loader's plugin. For simplicities sake this is a builtin plugin - and cannot be disabled, or reloaded (since
 * quilt-loader can't reload itself to a different version). */
public class StandardQuiltPlugin extends BuiltinQuiltPlugin {

	private final Map<String, OptionalModIdDefintion> modDefinitions = new HashMap<>();

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
		addBuiltinMod(new BuiltinMod(Collections.singletonList(javaPath), javaMeta.build()), "java");
	}

	private void addBuiltinMod(BuiltinMod mod, String name) {
		Path path;
		if (mod.paths.size() == 1) {
			path = mod.paths.get(0);
		} else {
			path = new QuiltJoinedFileSystem(name, mod.paths).getRoot();
		}

		// We don't go via context().addModOption since we don't really have a good gui node to base it off
		context().ruleContext().addOption(new BuiltinModOption(context(), mod.metadata, path, path));
	}

	@Override
	public ModLoadOption[] scanZip(Path root, PluginGuiTreeNode guiNode) throws IOException {

		Path parent = context().manager().getParent(root);

		if (!parent.getFileName().toString().endsWith(".jar")) {
			return null;
		}

		return scan0(root, false, guiNode);
	}

	@Override
	public ModLoadOption[] scanClasspathFolder(Path folder, PluginGuiTreeNode guiNode) throws IOException {
		return scan0(folder, true, guiNode);
	}

	private ModLoadOption[] scan0(Path root, boolean fromClasspath, PluginGuiTreeNode guiNode) throws IOException {
		Path qmj = root.resolve("quilt.mod.json");
		if (!Files.isRegularFile(qmj)) {
			return null;
		}

		try {
			InternalModMetadata meta = ModMetadataReader.read(qmj);

			Path from = root;
			if (fromClasspath) {
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

				PluginGuiTreeNode jarNode = guiNode.addChild(jar, SortOrder.ALPHABETICAL_ORDER);
				context().addFileToScan(inner, jarNode);
			}

			boolean mandatory = fromClasspath || from.getFileSystem() == FileSystems.getDefault();

			return new ModLoadOption[] { new QuiltModOption(context(), meta, from, root, mandatory) };
		} catch (ParseException parse) {
			guiNode.addChild("TODO:TRANSLATE('invalid-quilt.mod.json %s', " + parse.getMessage() + ")")//
				.mainIcon(guiNode.manager().iconJsonFile()).setError(parse);
			// TODO: Work out how to handle errors!
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
				def = new OptionalModIdDefintion(ctx, mod.id());
				modDefinitions.put(mod.id(), def);
				ctx.addRule(def);
			}

			if (mod.isMandatory()) {
				ctx.addRule(new MandatoryModIdDefinition(mod));
			}

			if (metadata.shouldQuiltDefineProvides()) {
				Collection<? extends ProvidedMod> provides = metadata.provides();

				for (ProvidedMod provided : provides) {
					PluginGuiTreeNode guiNode = context().manager().getGuiNode(mod)//
						.addChild("Providing " + provided.id());
					guiNode.mainIcon(guiNode.manager().iconUnknownFile());
					context().addModLoadOption(new ProvidedModOption(mod, provided), guiNode);
				}
			}

			if (metadata.shouldQuiltDefineDependencies()) {

				for (ModDependency dep : metadata.depends()) {
					ctx.addRule(createModDepLink(ctx, mod, dep));
				}

				for (ModDependency dep : metadata.breaks()) {
					ctx.addRule(createModBreaks(ctx, mod, dep));
				}
			}
		}
	}

	public static QuiltRuleDep createModDepLink(RuleContext ctx, LoadOption option, ModDependency dep) {

		if (dep instanceof ModDependency.Any) {
			ModDependency.Any any = (ModDependency.Any) dep;

			return new QuiltRuleDepAny(ctx, option, any);
		} else {
			ModDependency.Only only = (ModDependency.Only) dep;

			return new QuiltRuleDepOnly(ctx, option, only);
		}
	}

	public static QuiltRuleBreak createModBreaks(RuleContext ctx, LoadOption option, ModDependency dep) {
		if (dep instanceof ModDependency.All) {
			ModDependency.All any = (ModDependency.All) dep;

			return new QuiltRuleBreakAll(ctx, option, any);
		} else {
			ModDependency.Only only = (ModDependency.Only) dep;

			return new QuiltRuleBreakOnly(ctx, option, only);
		}
	}
}
