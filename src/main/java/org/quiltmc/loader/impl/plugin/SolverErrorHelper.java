/*
 * Copyright 2022 QuiltMC
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

package org.quiltmc.loader.impl.plugin;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.quiltmc.loader.api.plugin.QuiltPluginError;
import org.quiltmc.loader.api.plugin.gui.Text;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.Rule;
import org.quiltmc.loader.api.plugin.solver.RuleContext;
import org.quiltmc.loader.impl.plugin.gui.GuiManagerImpl;
import org.quiltmc.loader.impl.plugin.quilt.MandatoryModIdDefinition;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleDepOnly;

class SolverErrorHelper {

	static void reportSolverError(QuiltPluginManagerImpl manager, Collection<Rule> rules) {

		List<RuleLink> links = new ArrayList<>();
		Map<LoadOption, OptionLink> option2Link = new HashMap<>();

		for (Rule rule : rules) {
			RuleLink ruleLink = new RuleLink(rule);
			links.add(ruleLink);

			for (LoadOption from : rule.getNodesFrom()) {
				OptionLink optionLink = option2Link.computeIfAbsent(from, OptionLink::new);
				ruleLink.from.add(optionLink);
				optionLink.to.add(ruleLink);
			}

			for (LoadOption from : rule.getNodesTo()) {
				OptionLink optionLink = option2Link.computeIfAbsent(from, OptionLink::new);
				ruleLink.to.add(optionLink);
				optionLink.from.add(ruleLink);
			}
		}

		List<RuleLink> rootRules = new ArrayList<>();
		for (RuleLink link : links) {
			if (link.from.isEmpty()) {
				rootRules.add(link);
			}
		}

		if (reportKnownSolverError(manager, rootRules)) {
			return;
		}

		QuiltPluginError error = manager.theQuiltPluginContext.reportError(
			Text.translate("error.unhandled_solver")
		);
		error.appendDescription(Text.of("error.unhandled_solver.desc"));
		error.addOpenLinkButton(Text.of("error.unhandled_solver.button.quilt_loader_github"), "https://github.com/QuiltMC/quilt-loader/issues");
		error.appendReportText("Unhandled solver error involving the following rules:");

		StringBuilder sb = new StringBuilder();
		int number = 1;
		for (Rule rule : rules) {
			error.appendReportText("Rule " + number++ + ":");
			sb.setLength(0);
			// TODO: Rename 'fallbackErrorDescription'
			// to something like 'fallbackReportDescription'
			// and then clean up all of the implementations to be more readable.
			rule.fallbackErrorDescription(sb);
			error.appendReportText(sb.toString());
		}
	}

	static class RuleLink {
		final Rule rule;

		final List<OptionLink> from = new ArrayList<>();
		final List<OptionLink> to = new ArrayList<>();

		RuleLink(Rule rule) {
			this.rule = rule;
		}

		void addTo(OptionLink to) {
			this.to.add(to);
			to.from.add(this);
		}

		void addFrom(OptionLink from) {
			this.from.add(from);
			from.to.add(this);
		}
	}

	static class OptionLink {
		final LoadOption option;

		final List<RuleLink> from = new ArrayList<>();
		final List<RuleLink> to = new ArrayList<>();

		OptionLink(LoadOption option) {
			this.option = option;
			if (RuleContext.isNegated(option)) {
				throw new IllegalArgumentException("Call 'OptionLinkBase.get' instead of this!!");
			}
		}
	}

	private static boolean reportKnownSolverError(QuiltPluginManagerImpl manager, List<RuleLink> rootRules) {
		if (rootRules.isEmpty()) {
			return false;
		}

		if (rootRules.size() == 1) {
			RuleLink rootRule = rootRules.get(0);

			if (rootRule.rule instanceof MandatoryModIdDefinition) {
				return reportSingleMandatoryError(manager, rootRule);
			}
		}

		return false;
	}

	/** Reports an error where there is only one root rule, of a {@link MandatoryModIdDefinition}. */
	private static boolean reportSingleMandatoryError(QuiltPluginManagerImpl manager, RuleLink rootRule) {
		GuiManagerImpl guiManager = manager.guiManager;
		MandatoryModIdDefinition def = (MandatoryModIdDefinition) rootRule.rule;
		ModLoadOption mandatoryMod = def.option;

		if (rootRule.to.size() != 1) {//
			// This should always be the case, since a mandatory definition
			// always defines to a single source
			return false;
		}

		// TODO: Put this whole thing in a loop
		// and then check for transitive (and fully valid) dependency paths!

		OptionLink modLink = rootRule.to.get(0);

		if (modLink.to.isEmpty()) {
			// Apparently nothing is stopping this mod from loading
			// (so there's a bug here somewhere)
			return false;
		}

		// FOR NOW
		// just handle a single dependency / problem at a time
		if (modLink.to.size() > 1) {
			return false;
		}

		RuleLink mandatoryRule = modLink.to.get(0);

		if (mandatoryRule.rule instanceof QuiltRuleDepOnly) {
			QuiltRuleDepOnly dep = (QuiltRuleDepOnly) mandatoryRule.rule;

			if (dep.getValidOptions().isEmpty()) {
				// The root mod depends on a mod which is either missing, or is the wrong version
				if (dep.getWrongOptions().isEmpty()) {
					// Completely missing

					boolean transitive = false;
					boolean anyVersion = false;

					// Title:
					// "BuildCraft" [transitively] requires [version 1.5.1] of "Quilt Standard Libraries", which is
					// missing!

					// Description:
					// BuildCraft is loaded from '<mods>/buildcraft-9.0.0.jar'

					String titleKey = "error.dep." + (transitive ? "transitive" : "direct") //
						+ (anyVersion ? ".any" : ".versioned") + ".title";
					Object[] titleData = new Object[anyVersion ? 2 : 3];
					String rootModName = mandatoryMod.metadata().name();
					titleData[0] = rootModName;
					if (!anyVersion) {
						titleData[1] = "version [TODO:GET_VERSION]";
					}
					titleData[anyVersion ? 1 : 2] = getDepName(dep);
					Text title = Text.translate(titleKey, titleData);
					QuiltPluginError error = manager.theQuiltPluginContext.reportError(title);

					// TODO: Only upload a ModLoadOption's icon once!
					Map<Integer, BufferedImage> modIcons = new HashMap<>();
					for (int size : new int[]{16, 32}) {
						String iconPath = mandatoryMod.metadata().icon(size);
						if (iconPath != null) {
							Path path = mandatoryMod.resourceRoot().resolve(iconPath);
							try (InputStream stream = Files.newInputStream(path)) {
								BufferedImage image = ImageIO.read(stream);
								modIcons.put(image.getWidth(), image);
							} catch (IOException io) {
								// TODO: Warn about this somewhere!
								io.printStackTrace();
							}
						}
					}

					if (!modIcons.isEmpty()) {
						error.setIcon(manager.guiManager.allocateIcon(modIcons));
					}

					Path rootModPath = mandatoryMod.from();
					Object[] rootModDescArgs = { rootModName, rootModPath };
					error.appendDescription(Text.translate("info.root_mod_loaded_from", rootModDescArgs));

					error.addFileViewButton(Text.translate("button.view_file", rootModPath.getFileName()), rootModPath)
						.icon(mandatoryMod.modCompleteIcon());

					String issuesUrl = mandatoryMod.metadata().contactInfo().get("issues");
					if (issuesUrl != null) {
						error.addOpenLinkButton(Text.translate("button.mod_issue_tracker", mandatoryMod.metadata().name()), issuesUrl);
					}

					StringBuilder report = new StringBuilder(rootModName);
					if (transitive) {
						report.append(" transitively");
					}
					report.append(" requires");
					if (anyVersion) {
						report.append(" any version of ");
					} else {
						report.append(" version [TODO:GET_VERSION] of ");
					}
					report.append(getDepName(dep));
					report.append(", which is missing!");
					error.appendReportText(report.toString(), rootModName + " is loaded from " + rootModPath);

					return true;
				} else {

				}
			}

			// TODO!
			return false;

		} else {
			// Unhandled, at least for now
			return false;
		}
	}

	private static String getDepName(QuiltRuleDepOnly dep) {
		String id = dep.publicDep.id().id();
		switch (id) {
			case "fabric":
				return "Fabric API";
			default:
				return "'" + id + "'";
		}
	}
}
