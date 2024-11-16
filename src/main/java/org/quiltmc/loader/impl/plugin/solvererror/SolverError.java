package org.quiltmc.loader.impl.plugin.solvererror;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.VersionInterval;
import org.quiltmc.loader.api.gui.QuiltDisplayedError;
import org.quiltmc.loader.api.gui.QuiltLoaderGui;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.impl.plugin.SolverErrorReportContext;
import org.quiltmc.loader.impl.plugin.VersionRangeDescriber;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleDep;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleDepAny;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleDepOnly;
import org.quiltmc.loader.impl.util.FileUtil;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

/**
 * A solver error which might have multiple real errors merged into one for display.
 */
public abstract class SolverError {
	/**
	 * Attempts to merge this error into the given error. This object itself shouldn't be modified.
	 *
	 * @return True if the destination object was modified (and this was merged into it), false otherwise.
	 */
	public abstract boolean mergeInto(SolverError into);


	public abstract boolean isRelatedOption(LoadOption option);

	/**
	 * Adds the error to the plugin manager.
	 */
	public abstract void report(SolverErrorReportContext context);


	/**
	 * Adds the unless clause to the error.
	 *
	 * @param error     the error
	 * @param dep       the dependency containing the unless
	 * @param unlessDep the rule for the unless
	 */
	protected void addUnless(QuiltDisplayedError error, ModDependency.Only dep, QuiltRuleDep unlessDep) {
		if (dep.unless() instanceof ModDependency.Only) {
			ModDependency.Only unless = (ModDependency.Only) dep.unless();
			assert unless != null;
			QuiltLoaderText versionText = VersionRangeDescriber.describe(unless.versionRange(), unless.id().id());

			QuiltRuleDepOnly unlessRule = ((QuiltRuleDepOnly) unlessDep);
			if (unlessRule.getNodesTo().isEmpty()) {
				error.appendDescription(QuiltLoaderText.translate("error.break.unless.missing", versionText));
			} else {
				error.appendDescription(QuiltLoaderText.translate("error.break.unless.invalid", versionText));
			}
		} else if (dep.unless() instanceof ModDependency.Any) {
			ModDependency.Any unless = (ModDependency.Any) dep.unless();
			assert unless != null;

			error.appendDescription(QuiltLoaderText.translate("error.break.unless_any"));

			for (QuiltRuleDepOnly only : ((QuiltRuleDepAny) unlessDep).options) {
				QuiltLoaderText versionText = VersionRangeDescriber.describe(only.publicDep.versionRange(), only.publicDep.id().id());
				if (only.getNodesTo().isEmpty()) {
					error.appendDescription(QuiltLoaderText.translate("error.break.unless_any.missing", versionText));
				} else {
					error.appendDescription(QuiltLoaderText.translate("error.break.unless_any.invalid", versionText));
				}
			}
		}
	}

	/**
	 * Adds the following mod load options as buttons to the error.
	 *
	 * @param error         the error
	 * @param context       the plugin manager to get the paths to the mods
	 * @param optionsReason the key to the reason for these mods to be mentioned
	 * @param mods          a collection of mods to list
	 */
	protected final void addFiles(QuiltDisplayedError error, SolverErrorReportContext context, String optionsReason, Collection<ModLoadOption> mods) {
		Map<Path, ModLoadOption> realPaths = new LinkedHashMap<>();

		if (!mods.isEmpty()) {
			error.appendDescription(QuiltLoaderText.translate(optionsReason));
		}
		for (ModLoadOption mod : mods) {
			boolean provided = context.getGraph().isProvided(mod);
			boolean depended = context.getGraph().isDepended(mod);
			boolean mandatory = context.getGraph().isMandatory(mod);
			// TODO: describe the version?
			Object[] modDescArgs = {mod.id(), context.getManager().describePath(mod.from())};
			String key = "info.";
			if (provided) {
				key += "provided_";
			}
			if (depended) {
				key += "depended_";
			}
			if (!mandatory) {
				key += "optional_";
			}
			key += "mod_loaded_from";

			error.appendDescription(QuiltLoaderText.translate(key, modDescArgs));
			context.getManager().getRealContainingFile(mod.from()).ifPresent(p -> realPaths.putIfAbsent(p, mod));
		}

		for (Map.Entry<Path, ModLoadOption> entry : realPaths.entrySet()) {
			error.addFileViewButton(entry.getKey()).icon(entry.getValue().modCompleteIcon());
		}
	}

	/**
	 * Adds the issue link from the mod to the error.
	 *
	 * @param error        the error
	 * @param mandatoryMod the mod
	 */
	protected void addIssueLink(QuiltDisplayedError error, ModLoadOption mandatoryMod) {
		String issuesUrl = mandatoryMod.metadata().contactInfo().get("issues");
		if (issuesUrl != null) {
			error.addOpenLinkButton(QuiltLoaderText.translate("button.mod_issue_tracker", mandatoryMod.metadata().name()), issuesUrl);
		}
	}

	/**
	 * Adds the mod icon to the error.
	 *
	 * @param error        the error
	 * @param mandatoryMod the mod to get the icon from
	 */
	protected void setIconFromMod(QuiltDisplayedError error, ModLoadOption mandatoryMod) {
		// TODO: Only upload a ModLoadOption's icon once!
		Map<String, byte[]> images = new HashMap<>();
		for (int size : new int[]{16, 32}) {
			String iconPath = mandatoryMod.metadata().icon(size);
			if (iconPath != null && !images.containsKey(iconPath)) {
				Path path = mandatoryMod.resourceRoot().resolve(iconPath);
				try (InputStream stream = Files.newInputStream(path)) {
					images.put(iconPath, FileUtil.readAllBytes(stream));
				} catch (IOException io) {
					Log.error(LogCategory.SOLVING, "Error setting GUI icon for mod %s", mandatoryMod.metadata().name(), io);
				}
			}
		}

		if (!images.isEmpty()) {
			error.setIcon(QuiltLoaderGui.createIcon(images.values().toArray(new byte[0][])));
		}
	}

	/**
	 * Gets a description for the specified load option, including some relations and the path. It follows the format:
	 * {@code [[Needed] <Mandatory|Optional> '<mod_name>'] [version '<version>'] [Provided by '<providing_mod_name>'] [, which is contained within '<containing_mod_name>']: <path>}.
	 *
	 * @param option       the mod load option
	 * @param context      the error context
	 * @param addName      {@code true} if the name should be added to the mod line. Ignored if the mod is provided
	 * @param addVersion   {@code true} if the version string should be added to the mod line
	 * @param isDependency {@code true} if the mod is already part of a dependency error
	 * @return the mod load option description
	 */
	protected List<String> getModReportLine(ModLoadOption option, SolverErrorReportContext context, boolean addName, boolean addVersion, boolean isDependency) {
		boolean provided = context.getGraph().isProvided(option);
		boolean depended = context.getGraph().isDepended(option);
		boolean optional = !context.getGraph().isMandatory(option);

		StringBuilder lineBuilder = new StringBuilder("- ");
		List<String> lines = new ArrayList<>();

		if (provided) {
			// This cast is fine because only mod load options can be provided
			ModLoadOption providingMod = context.getGraph().getProvidingMod(option);
			lineBuilder.append("Provided by '").append(providingMod.metadata().name()).append("'");
		} else {
			if (!isDependency) {
				if (depended) {
					lineBuilder.append("Needed");
				} else if (optional) {
					lineBuilder.append("Optional");
				}
			}

			if (addName) {
				if ((depended || optional) && !isDependency) {
					lineBuilder.append(" ");
				}
				lineBuilder.append("'").append(option.metadata().name()).append("'");
			}

			if (addVersion) {
				if ((depended || optional) && !isDependency || addName) {
					lineBuilder.append(" v");
				} else {
					lineBuilder.append("V");
				}
				lineBuilder.append("ersion '").append(option.version()).append("'");
			}
		}

		if (option.getContainingMod() != null) {
			ModLoadOption containingMod = option;
			List<ModLoadOption> containers = new ArrayList<>();
			while (containingMod.getContainingMod() != null) {
				containers.add(containingMod);
				containingMod = containingMod.getContainingMod();
			}
			containers.add(containingMod);

			lineBuilder.append(", which is contained within '").append(containingMod.metadata().name()).append("' ")
					.append("@ ").append(context.getManager().describePath(containingMod.from())).toString();
			lines.add(lineBuilder.toString());
			lineBuilder = new StringBuilder();

			if (containers.size() > 2) {
				lineBuilder.append("  - Note: '").append(option.metadata().name()).append("' is contained within '").append(containingMod.metadata().name()).append("' through the chain ");
				Collections.reverse(containers);

				lineBuilder.append(containers.stream().map(ModLoadOption::metadata).map(ModMetadataExt::name)
						.map(name -> "'" + name + "'")
						.collect(Collectors.joining(" -> ")));
				lines.add(lineBuilder.toString());
			}
		} else {
			if (provided || addName || addVersion) {
				lineBuilder.append(" ");
			}

			String line = lineBuilder.append("@ ").append(context.getManager().describePath(option.from())).toString();
			lines.add(line);
		}
		return lines;
	}

	/**
	 * Adds the version string for the dependency to the string builder, and whitespace is added around the string.
	 * If {@code start} is true, this is expected to be the start of a new line, and no leading space is added.
	 *
	 * @param report the string builder to inline the string into
	 * @param only   the dependency
	 * @param breaks {@code true} if this is a breaking dependency, {@code false} if it is a required dependency
	 * @param start  {@code true} if this is the start of a line, {@code false} otherwise.
	 */
	protected void addVersionString(StringBuilder report, ModDependency.Only only, boolean breaks, boolean start) {
		StringBuilder version = new StringBuilder();
		if (breaks) {
			version.append(" all versions");
		} else {
			version.append(" any version");
		}

		if (only.versionRange().size() == 1) {
			VersionInterval interval = only.versionRange().first();

			if (interval.getMin() == null) { // neg infinity
				if (interval.getMax() == null) {
					// any
					version.append(" of ");
				} else {
					if (interval.isMaxInclusive()) {
						version.append(" lesser than or equal to ")
								.append(interval.getMax())
								.append(" of ");
					} else {
						version.append(" lesser than ")
								.append(interval.getMax())
								.append(" of ");
					}
				}
			} else {
				if (interval.getMax() == null) { // pos infinity
					if (interval.isMinInclusive()) {
						version.append(" greater than or equal to ")
								.append(interval.getMin())
								.append(" of ");
					} else {
						version.append(" greater than ")
								.append(interval.getMin())
								.append(" of ");
					}
				} else {
					if (interval.getMin().equals(interval.getMax())) {
						version = new StringBuilder(" exactly version ")
								.append(interval.getMin())
								.append(" of ");
					} else {
						if (interval.isMinInclusive()) {
							version.append(" >=");
						} else {
							version.append(" >");
						}

						version.append(interval.getMin()).append(" and ");

						if (interval.isMaxInclusive()) {
							version.append("<=");
						} else {
							version.append("<");
						}

						version.append(interval.getMax()).append(" of ");
					}
				}
			}
		} else {
			version = new StringBuilder(" a version ").append(only.versionRange()).append(" of ");
		}

		String versionString = version.toString();
		if (start) {
			char second = versionString.charAt(1);
			versionString = Character.toUpperCase(second) + versionString.substring(2);
		}

		report.append(versionString);
	}

	/**
	 * Describes the unless clause for a breaking dependency. We do not handle a requiring dependency, since those algebraically resolve to a DepAny.
	 *
	 * @param dep             the dependency containing the unless
	 * @param dependentName   the name of the dependent mod(s)
	 * @param unlessDepRule   the rule specifying the unless
	 * @param singleDependent {@code true} if there is only one dependent mod, {@code false} if there are multiple
	 * @return the lines describing the unless clause
	 */
	protected List<String> addUnlessClause(ModDependency.Only dep, String dependentName, QuiltRuleDep unlessDepRule, boolean singleDependent) {
		StringBuilder report = new StringBuilder();
		if (dep.unless() instanceof ModDependency.Only) {
			ModDependency.Only unless = (ModDependency.Only) dep.unless();
			assert unless != null;
			QuiltRuleDepOnly unlessRule = ((QuiltRuleDepOnly) unlessDepRule);
			if (unlessRule.getNodesTo().isEmpty()) {
				report.append("However, if");

				addVersionString(report, unless, false, false);

				report.append(unless.id()).append(" is present, ").append(dependentName).append(" do");
				if (singleDependent) {
					report.append("es");
				}
				report.append(" not break ");

				report.append(dep.id()).append(".");
			} else {
				report.append("Normally,");

				addVersionString(report, unless, false, false);

				report.append("mod ").append(unless.id()).append(" overrides this break, but it is unable to load due to another error.");
			}

			return Collections.singletonList(report.toString());
		} else if (dep.unless() instanceof ModDependency.Any) {
			QuiltRuleDepAny unlessRule = ((QuiltRuleDepAny) unlessDepRule);
			List<String> lines = new ArrayList<>();
			report.append("However, if any of the following are present, ");
			report.append(dependentName).append(" do");
			if (singleDependent) {
				report.append("es");
			}
			report.append(" not break.");
			lines.add(report.toString());

			for (QuiltRuleDepOnly only : unlessRule.options) {
				report = new StringBuilder("- ");
				addVersionString(report, only.publicDep, false, true);
				report.append(only.publicDep.id());

				if (only.getAllOptions().isEmpty()) {
					report.append(" which is missing.");
				} else {
					report.append(" which is unable to load due to another error.");
				}
				lines.add(report.toString());
			}
			return lines;
		}
		return Collections.emptyList();
	}
}
