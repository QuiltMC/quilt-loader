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

package org.quiltmc.loader.impl.plugin;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

import org.quiltmc.loader.api.ModDependencyIdentifier;
import org.quiltmc.loader.api.ModMetadata.ProvidedMod;
import org.quiltmc.loader.api.gui.QuiltDisplayedError;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.VersionRange;
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

	private static final boolean LOG_ERROR_REASONING = true;

	static void reportSolverError(QuiltPluginManagerImpl manager, Map<ModDependencyIdentifier, Error> errors, Collection<Rule> rules) {

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

		if (reportAndCollectKnownSolverError(manager, errors, rootRules)) {
			return;
		}

		QuiltDisplayedError error = manager.theQuiltPluginContext.reportError(
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

	private static boolean reportAndCollectKnownSolverError(QuiltPluginManagerImpl manager, Map<ModDependencyIdentifier, Error> errors, List<RuleLink> rootRules) {
		if (rootRules.isEmpty()) {
			return false;
		}

		if (rootRules.size() == 1) {
			RuleLink rootRule = rootRules.get(0);
			// TODO: handle unreported errors better
			return reportMandatoryErrors(manager, errors, rootRule).isEmpty();
		}

		if (reportDuplicateMandatoryMods(manager, rootRules)) {
			return true;
		}

		return false;
	}


	private static Map<RuleLink, List<RuleLink>> reportMandatoryErrors(QuiltPluginManagerImpl manager, Map<ModDependencyIdentifier, Error> errors, RuleLink rootRule) {
		// Map of roots to their ends
		Map<RuleLink, List<RuleLink>> ends = new HashMap<>();

		MandatoryModIdDefinition def = (MandatoryModIdDefinition) rootRule.rule;
		ModLoadOption mandatoryMod = def.option;

		if (rootRule.to.size() != 1) {
			// This should always be the case since a mandatory mod always has a single source, right?
			throw new IllegalArgumentException("Mandatory definition has multiple sources?");
		}

		OptionLink modLink = rootRule.to.get(0);

		if (modLink.to.isEmpty()) {
			throw new IllegalStateException("Unexpected end of chain. Nothing is stopping this mod from loading?");
		}

		findEnds(r -> ends.computeIfAbsent(rootRule, (k) -> new ArrayList<>()).add(r), modLink);


		Map<RuleLink, List<RuleLink>> ret = new HashMap<>();
		// go over each end and try to convert it to an error
		ends.forEach((root, list) -> list.forEach(end -> {
			// we have goto at home
			// TODO: when this works, make the logic not a complete mess
			while (true) {
				if (end.rule instanceof QuiltRuleDepOnly) {
					QuiltRuleDepOnly dep = (QuiltRuleDepOnly) end.rule;

					Error err = errors.computeIfAbsent(dep.publicDep.id(), k->new Error());
					err.rules.add(end);
					err.range = err.range.combineMatchingBoth(dep.publicDep.versionRange());

					return;
				}
				break;
			}



			// exit case; unable to handle
			ret.computeIfAbsent(root, k -> new ArrayList<>()).add(end);
		}));

		// TODO: right now we ignore when groups are specified for dependencies
		return ends;
	}


	public static class Error {
		List<RuleLink> rules = new ArrayList<>();
		VersionRange range = VersionRange.ANY;
	}


	private static void findEnds(Consumer<RuleLink> list, OptionLink link) {
		if (link.to.isEmpty()) {
			link.from.forEach(list);
		}
		for (RuleLink ruleLink : link.to) {
			if (ruleLink.to.isEmpty()) {
				list.accept(ruleLink);
			} else {
				findEnds(list, link);
			}
		}
	}

	static void reportErrors(QuiltPluginManagerImpl manager) {

	}



	private static void setIconFromMod(QuiltPluginManagerImpl manager, ModLoadOption mandatoryMod,
		QuiltDisplayedError error) {
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
		QuiltDisplayedError error = manager.theQuiltPluginContext.reportError(title);
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