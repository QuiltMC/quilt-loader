package org.quiltmc.loader.impl.plugin;

import org.apache.logging.log4j.Logger;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.plugin.FullModMetadata;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.RuleContext;
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
	public void onLoadOptionAdded(LoadOption option) {
		if (option instanceof ModLoadOption) {
			ModLoadOption mod = (ModLoadOption) option;
			FullModMetadata metadata = mod.metadata();

			if (metadata.isQuiltDeps()) {

				RuleContext ctx = context().ruleContext();

				for (ModDependency dep : metadata.depends()) {
					ctx.addRule(createModDepLink(logger, ctx, mod, dep));
				}

				for (ModDependency dep : metadata.breaks()) {
					ctx.addRule(createModBreaks(logger, ctx, mod, dep));
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
