package org.quiltmc.loader.impl.plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.json5.exception.ParseException;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.plugin.FullModMetadata;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode.SortOrder;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.RuleContext;
import org.quiltmc.loader.impl.metadata.qmj.InternalModMetadata;
import org.quiltmc.loader.impl.metadata.qmj.ModMetadataReader;
import org.quiltmc.loader.impl.metadata.qmj.ModProvided;
import org.quiltmc.loader.impl.solver.QuiltModOption;
import org.quiltmc.loader.impl.solver.QuiltRuleBreak;
import org.quiltmc.loader.impl.solver.QuiltRuleBreakAll;
import org.quiltmc.loader.impl.solver.QuiltRuleBreakOnly;
import org.quiltmc.loader.impl.solver.QuiltRuleDep;
import org.quiltmc.loader.impl.solver.QuiltRuleDepAny;
import org.quiltmc.loader.impl.solver.QuiltRuleDepOnly;

/** Quilt-loader's plugin. For simplicities sake this is a builtin plugin - and cannot be disabled, or reloaded (since
 * quilt-loader can't reload itself to a different version). */
public class StandardQuiltPlugin extends BuiltinQuiltPlugin {

	@Override
	public @Nullable ModLoadOption scanZip(Path root, PluginGuiTreeNode guiNode) throws IOException {

		Path parent = context().manager().getParent(root);

		if (!parent.getFileName().toString().endsWith(".jar")) {
			return null;
		}

		return scan0(root, false, guiNode);
	}

	@Override
	public ModLoadOption scanClasspathFolder(Path folder, PluginGuiTreeNode guiNode) throws IOException {
		return scan0(folder, true, guiNode);
	}

	private ModLoadOption scan0(Path root, boolean fromClasspath, PluginGuiTreeNode guiNode) throws IOException {
		Path qmj = root.resolve("quilt.mod.json");
		if (!Files.isRegularFile(qmj)) {
			return null;
		}

		try {
			InternalModMetadata meta = ModMetadataReader.read((Logger) null, qmj);

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

			return new QuiltModOption(context().manager(), meta, from, root);
		} catch (ParseException parse) {
			guiNode.addChild("TODO:TRANSLATE('invalid-quilt.mod.json %s', " + parse.getMessage() + ")")//
				.setError(parse);
			return null;
		}
	}

	@Override
	public void onLoadOptionAdded(LoadOption option) {
		if (option instanceof ModLoadOption) {
			ModLoadOption mod = (ModLoadOption) option;
			FullModMetadata metadata = mod.metadata();

			Collection<ModProvided> provides = metadata.provides();

			// TODO: Add provided mods as options!

			if (metadata.isQuiltDeps()) {

				RuleContext ctx = context().ruleContext();

				for (ModDependency dep : metadata.depends()) {
					ctx.addRule(createModDepLink((Logger) null, ctx, mod, dep));
				}

				for (ModDependency dep : metadata.breaks()) {
					ctx.addRule(createModBreaks((Logger) null, ctx, mod, dep));
				}
			}
		}
	}

	public static QuiltRuleDep createModDepLink(Logger logger, RuleContext ctx, LoadOption option, ModDependency dep) {

		if (dep instanceof ModDependency.Any) {
			ModDependency.Any any = (ModDependency.Any) dep;

			return new QuiltRuleDepAny(logger, ctx, option, any);
		} else {
			ModDependency.Only only = (ModDependency.Only) dep;

			return new QuiltRuleDepOnly(logger, ctx, option, only);
		}
	}

	public static QuiltRuleBreak createModBreaks(Logger logger, RuleContext ctx, LoadOption option, ModDependency dep) {
		if (dep instanceof ModDependency.All) {
			ModDependency.All any = (ModDependency.All) dep;

			return new QuiltRuleBreakAll(logger, ctx, option, any);
		} else {
			ModDependency.Only only = (ModDependency.Only) dep;

			return new QuiltRuleBreakOnly(logger, ctx, option, only);
		}
	}
}
