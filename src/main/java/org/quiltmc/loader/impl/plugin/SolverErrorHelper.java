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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.imageio.ImageIO;

import org.quiltmc.loader.api.ModDependencyIdentifier;
import org.quiltmc.loader.api.ModMetadata.ProvidedMod;
import org.quiltmc.loader.api.VersionRange;
import org.quiltmc.loader.api.plugin.QuiltPluginError;
import org.quiltmc.loader.api.plugin.gui.QuiltLoaderText;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.Rule;
import org.quiltmc.loader.api.plugin.solver.RuleContext;
import org.quiltmc.loader.impl.plugin.quilt.DisabledModIdDefinition;
import org.quiltmc.loader.impl.plugin.quilt.MandatoryModIdDefinition;
import org.quiltmc.loader.impl.plugin.quilt.OptionalModIdDefintion;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleDepOnly;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
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
			QuiltLoaderText.translate("error.unhandled_solver")
		);
		error.appendDescription(QuiltLoaderText.of("error.unhandled_solver.desc"));
		error.addOpenLinkButton(QuiltLoaderText.of("button.quilt_loader_report"), "https://github.com/QuiltMC/quilt-loader/issues");
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

			return false;
		}

		if (reportDuplicateMandatoryMods(manager, rootRules)) {
			return true;
		}

		return false;
	}

	/** Reports an error where there is only one root rule, of a {@link MandatoryModIdDefinition}. */
	private static boolean reportSingleMandatoryError(QuiltPluginManagerImpl manager, RuleLink rootRule) {
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
		List<OptionLink> modLinks = Collections.singletonList(modLink);
		Set<OptionLink> nextModLinks = new LinkedHashSet<>();
		List<List<OptionLink>> fullChain = new ArrayList<>();

		String groupOn = null;
		String modOn = null;

		while (true) {
			groupOn = null;
			modOn = null;
			Boolean areAllInvalid = null;
			nextModLinks = new LinkedHashSet<>();

			for (OptionLink link : modLinks) {
				if (link.to.isEmpty()) {
					// Apparently nothing is stopping this mod from loading
					// (so there's a bug here somewhere)
					return false;
				}

				if (link.to.size() > 1) {
					// FOR NOW
					// just handle a single dependency / problem at a time
					return false;
				}

				RuleLink rule = link.to.get(0);
				if (rule.rule instanceof QuiltRuleDepOnly) {
					QuiltRuleDepOnly dep = (QuiltRuleDepOnly) rule.rule;

					ModDependencyIdentifier id = dep.publicDep.id();
					if (groupOn == null) {
						if (!id.mavenGroup().isEmpty()) {
							groupOn = id.mavenGroup();
						}
					} else if (!id.mavenGroup().isEmpty() && !groupOn.equals(id.mavenGroup())) {
						// A previous dep targets a different group of the same mod, so this is a branching condition
						return false;
					}

					if (modOn == null) {
						modOn = id.id();
					} else if (!modOn.equals(id.id())) {
						// A previous dep targets a different mod, so this is a branching condition
						return false;
					}

					if (dep.publicDep.unless() != null) {
						// TODO: handle 'unless' clauses!
						return false;
					}

					if (dep.getValidOptions().isEmpty()) {
						// Loop exit condition!
						if (areAllInvalid != null && areAllInvalid) {
							continue;
						} else if (nextModLinks.isEmpty()) {
							areAllInvalid = true;
							continue;
						} else {
							// Some deps are mismatched, others aren't
							// so this isn't necessarily a flat dep chain
							// (However it could be if the chain ends with a mandatory mod
							// like minecraft, which doesn't have anything else at the end of the chain
							return false;
						}
					}

					if (dep.getAllOptions().size() != rule.to.size()) {
						return false;
					}

					// Now check that they all match up
					for (ModLoadOption option : dep.getAllOptions()) {
						boolean found = false;
						for (OptionLink link2 : rule.to) {
							if (option == link2.option) {
								found = true;
								if (dep.getValidOptions().contains(option)) {
									nextModLinks.add(link2);
								}
							}
						}
						if (!found) {
							return false;
						}
					}

				} else {
					// TODO: Handle other conditions!
					return false;
				}
			}

			fullChain.add(modLinks);

			if (areAllInvalid != null && areAllInvalid) {
				// Now we have validated that every mod in the previous list all depend on the same mod

				// Technically we should think about how to handle multiple *conflicting* version deps.
				// For example:
				// buildcraft requires abstract_base *
				// abstract_base 18 requires minecraft 1.18.x
				// abstract_base 19 requires minecraft 1.19.x

				// Technically we are just showing deps as "transitive" so
				// we just say that buildcraft requires minecraft 1.18.x or 1.19.x
				// so we need to make the required version list "bigger" rather than smaller

				// This breaks down if "abstract_base" requires an additional library though.
				// (Although we aren't handling that here)

				VersionRange fullRange = VersionRange.NONE;
				Set<ModLoadOption> allInvalidOptions = new HashSet<>();
				for (OptionLink link : fullChain.get(fullChain.size() - 1)) {
					// We validate all this earlier
					QuiltRuleDepOnly dep = (QuiltRuleDepOnly) link.to.get(0).rule;
					fullRange = VersionRange.ofRanges(Arrays.asList(fullRange, dep.publicDep.versionRange()));
					allInvalidOptions.addAll(dep.getWrongOptions());
				}

				boolean transitive = fullChain.size() > 1;
				boolean anyVersion = VersionRange.ANY.equals(fullRange);
				boolean missing = allInvalidOptions.isEmpty();

				// Title:
				// "BuildCraft" [transitively] requires [version 1.5.1] of "Quilt Standard Libraries", which is
				// missing!

				// Description:
				// BuildCraft is loaded from '<mods>/buildcraft-9.0.0.jar'

				String titleKey = "error.dep."//
					+ (transitive ? "transitive" : "direct") //
					+ (anyVersion ? ".any" : ".versioned") //
					+ ".title";
				Object[] titleData = new Object[anyVersion ? 2 : 3];
				String rootModName = mandatoryMod.metadata().name();
				titleData[0] = rootModName;
				if (!anyVersion) {
					// TODO: Turn this ugly range into a human-readable version specifier!
					titleData[1] = "version " + fullRange;
				}
				titleData[anyVersion ? 1 : 2] = modOn;//getDepName(dep);
				QuiltLoaderText first = QuiltLoaderText.translate(titleKey, titleData);
				Object[] secondData = new Object[allInvalidOptions.size() == 1 ? 1 : 0];
				String secondKey = "error.dep.";
				if (missing) {
					secondKey += "missing";
				} else if (allInvalidOptions.size() > 1) {
					secondKey += "multi_mismatch";
				} else {
					secondKey += "single_mismatch";
					secondData[0] = allInvalidOptions.iterator().next().version().toString();
				}
				QuiltLoaderText second = QuiltLoaderText.translate(secondKey + ".title", secondData);
				QuiltLoaderText title = QuiltLoaderText.translate("error.dep.join.title", first, second);
				QuiltPluginError error = manager.theQuiltPluginContext.reportError(title);

				setIconFromMod(manager, mandatoryMod, error);

				Path rootModPath = mandatoryMod.from();
				Object[] rootModDescArgs = { rootModName, manager.describePath(rootModPath) };
				error.appendDescription(QuiltLoaderText.translate("info.root_mod_loaded_from", rootModDescArgs));

				for (List<OptionLink> list : fullChain.subList(1, fullChain.size())) {
					OptionLink firstMod = list.get(0);
					error.appendDescription(QuiltLoaderText.of("via " + ((ModLoadOption) firstMod.option).id()));
				}

				error.addFileViewButton(QuiltLoaderText.translate("button.view_file", rootModPath.getFileName()), rootModPath)
					.icon(mandatoryMod.modCompleteIcon());

				String issuesUrl = mandatoryMod.metadata().contactInfo().get("issues");
				if (issuesUrl != null) {
					error.addOpenLinkButton(QuiltLoaderText.translate("button.mod_issue_tracker", mandatoryMod.metadata().name()), issuesUrl);
				}

				StringBuilder report = new StringBuilder(rootModName);
				if (transitive) {
					report.append(" transitively");
				}
				report.append(" requires");
				if (anyVersion) {
					report.append(" any version of ");
				} else {
					report.append(" version " + fullRange + " of ");
				}
				report.append(modOn);// TODO
				report.append(", which is missing!");
				error.appendReportText(report.toString(), rootModName + " is loaded from " + rootModPath);

				return true;
			}

			modLinks = new ArrayList<>(nextModLinks);
		}
	}

	private static void setIconFromMod(QuiltPluginManagerImpl manager, ModLoadOption mandatoryMod,
		QuiltPluginError error) {
		// TODO: Only upload a ModLoadOption's icon once!
		Map<Integer, BufferedImage> modIcons = new HashMap<>();
		for (int size : new int[] { 16, 32 }) {
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

	private static boolean reportDuplicateMandatoryMods(QuiltPluginManagerImpl manager, List<RuleLink> rootRules) {
		// N+1 root rules for N duplicated mods:
		// 0 is the OptionalModIdDefintion
		// 1..N is either MandatoryModIdDefinition or DisabledModIdDefinition (and the mod is mandatory)

		if (rootRules.size() < 3) {
			return false; 
		}

		// Step 1: Find the single OptionalModIdDefinition
		OptionalModIdDefintion optionalDef = null;
		for (RuleLink link : rootRules) {
			if (link.rule instanceof OptionalModIdDefintion) {
				if (optionalDef != null) {
					return false;
				}
				optionalDef = (OptionalModIdDefintion) link.rule;
			}
		}

		if (optionalDef == null) {
			return false;
		}

		// Step 2: Check to see if the rest of the root rules are MandatoryModIdDefinition
		// and they share some provided id (or real id)
		List<ModLoadOption> mandatories = new ArrayList<>();
		Set<String> commonIds = null;
		for (RuleLink link : rootRules) {
			if (link.rule == optionalDef) {
				continue;
			}

			if (link.rule instanceof MandatoryModIdDefinition) {
				MandatoryModIdDefinition mandatory = (MandatoryModIdDefinition) link.rule;
				if (commonIds == null) {
					commonIds = new HashSet<>();
					commonIds.add(mandatory.getModId());
					for (ProvidedMod provided : mandatory.option.metadata().provides()) {
						commonIds.add(provided.id());
					}
				} else {
					Set<String> fromThis = new HashSet<>();
					fromThis.add(mandatory.getModId());
					for (ProvidedMod provided : mandatory.option.metadata().provides()) {
						fromThis.add(provided.id());
					}
					commonIds.retainAll(fromThis);
					if (commonIds.isEmpty()) {
						return false;
					}
				}
				mandatories.add(mandatory.option);
			} else if (link.rule instanceof DisabledModIdDefinition) {
				DisabledModIdDefinition disabled = (DisabledModIdDefinition) link.rule;
				if (!disabled.option.isMandatory()) {
					return false;
				}
				if (!commonIds.contains(disabled.getModId())) {
					return false;
				}
				commonIds.clear();
				commonIds.add(disabled.getModId());
			} else {
				return false;
			}
		}

		if (mandatories.isEmpty()) {
			// So this means there's an OptionalModIdDefintion with only
			// DisabledModIdDefinitions as roots
			// that means this isn't a duplicate mandatory mods error!
			return false;
		}
		ModLoadOption firstMandatory = mandatories.get(0);
		String bestName = null;
		for (ModLoadOption option : mandatories) {
			if (commonIds.contains(option.id())) {
				bestName = option.metadata().name();
				break;
			}
		}

		if (bestName == null) {
			bestName = "id'" + commonIds.iterator().next() + "'";
		}

		// Title:
		// Duplicate mod: "BuildCraft"

		// Description:
		// - "buildcraft-all-9.0.0.jar"
		// - "buildcraft-all-9.0.1.jar"
		// Remove all but one.

		// With buttons to view each mod individually

		QuiltLoaderText title = QuiltLoaderText.translate("error.duplicate_mandatory", bestName);
		QuiltPluginError error = manager.theQuiltPluginContext.reportError(title);
		error.appendReportText("Duplicate mandatory mod ids " + commonIds);
		setIconFromMod(manager, firstMandatory, error);

		for (ModLoadOption option : mandatories) {
			String path = manager.describePath(option.from());
			// Just in case
			Optional<Path> container = manager.getRealContainingFile(option.from());
			error.appendDescription(QuiltLoaderText.translate("error.duplicate_mandatory.mod", path));
			container.ifPresent(value -> error.addFileViewButton(QuiltLoaderText.translate("button.view_file", value.getFileName()), value)
					.icon(option.modCompleteIcon()));

			error.appendReportText("- " + path);
		}

		error.appendDescription(QuiltLoaderText.translate("error.duplicate_mandatory.desc"));

		return true;
	}
}