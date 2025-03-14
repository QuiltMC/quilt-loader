package org.quiltmc.loader.impl.plugin.solvererror;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.quiltmc.loader.api.gui.QuiltDisplayedError;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.impl.plugin.SolverErrorReportContext;
import org.quiltmc.loader.impl.plugin.quilt.ProvidedModOption;

/**
 * Reports on a set of rules that indicate multiple mods provide the same id.
 */
public class DuplicatesError extends SolverError {
	final String id;
	final Set<LoadOption> duplicates = new LinkedHashSet<>();

	public DuplicatesError(String id, Collection<LoadOption> duplicates) {
		this.id = id;
		this.duplicates.addAll(duplicates);
	}

	@Override
	public boolean isRelatedOption(LoadOption option) {
		return duplicates.contains(option) ||
			   this.duplicates.stream()
					   .filter(ProvidedModOption.class::isInstance)
					   .map(ProvidedModOption.class::cast)
					   .map(ProvidedModOption::getTarget)
					   .anyMatch(Predicate.isEqual(option));
	}

	@Override
	public boolean mergeInto(SolverError into) {
		if (into instanceof DuplicatesError) {
			DuplicatesError depDst = (DuplicatesError) into;
			if (!this.id.equals(depDst.id)) {
				return false;
			}
			depDst.duplicates.addAll(duplicates);
			return true;
		}
		return false;
	}

	@Override
	public void report(SolverErrorReportContext context) {
		List<ModLoadOption> mandatoryMods = this.duplicates.stream().filter(((Predicate<LoadOption>) context.getGraph()::isMandatory).or(context.getGraph()::isDepended)).map(ModLoadOption.class::cast).collect(Collectors.toList());

		if (mandatoryMods.isEmpty()) {
			// Somehow we have a duplicates error without any mandatory or depended mods.
			// TODO: Throw error?
			return;
		}

		// Try not to use a providing mod name
		ModLoadOption bestCandidate = mandatoryMods.stream().filter(((Predicate<LoadOption>) context.getGraph()::isProvided).negate()).findFirst().orElse(mandatoryMods.get(0));
		String bestName = bestCandidate.metadata().name();

		// Are the provided versions all the same?
		boolean sameVersion = duplicates.stream().filter(ModLoadOption.class::isInstance).map(ModLoadOption.class::cast).map(ModLoadOption::version).distinct().count() == 1;

		// Title:
		// Duplicate mod: "BuildCraft"

		// Description:
		// - "buildcraft-all-9.0.0.jar"
		// - "buildcraft-all-9.0.1.jar"
		// Remove all but one.

		// With buttons to view each mod individually

		QuiltLoaderText title = QuiltLoaderText.translate("error.duplicate_mandatory", bestName);
		QuiltDisplayedError error = context.createError(title);
		error.appendReportText("'" + bestName + "'" + (!bestName.equals(id) ? (" ('" + id + "')") : "") + " is provided by multiple files:");
		setIconFromMod(error, bestCandidate);

		List<LoadOption> duplicates = sortOptions(this.duplicates);

		for (LoadOption loadOption : duplicates) {
			if (loadOption instanceof ModLoadOption) {
				ModLoadOption option = ((ModLoadOption) loadOption);
				String path = context.getManager().describePath(option.from());
				Optional<Path> container = context.getManager().getRealContainingFile(option.from());

				// Just in case
				if (option instanceof ProvidedModOption) {
					error.appendDescription(QuiltLoaderText.translate("error.duplicate_mandatory.mod.provided", path));
				} else {
					error.appendDescription(QuiltLoaderText.translate("error.duplicate_mandatory.mod", path));
				}

				container.ifPresent(value -> error.addFileViewButton(QuiltLoaderText.translate("button.view_file", value.getFileName()), value).icon(option.modCompleteIcon()));

				getModReportLine(option, context, !option.metadata().name().equals(bestName), !sameVersion, true).forEach(error::appendReportText);
				if (!context.getGraph().isMandatory(option)) {
					if (context.getGraph().isDepended(option)) {
						error.appendReportText("  - '" + option.metadata().name() + "' is needed by another mod, see Error " + context.getRelatedErrors(option, this));
					} else {
						// I think its only that this is an unless?
						error.appendReportText("  - '" + option.metadata().name() + "' helps to resolve two or more mods that break each other, see Error " + context.getRelatedErrors(option, this));
					}
				}
			} else {
				error.appendDescription(QuiltLoaderText.translate("error.unhandled_mod_file.title", loadOption.describe()));
				error.appendReportText("- Unknown load option: " + loadOption.describe());
			}
		}

		error.appendDescription(QuiltLoaderText.translate("error.duplicate_mandatory.desc"));
	}

	private static List<LoadOption> sortOptions(Collection<LoadOption> options) {
		List<LoadOption> duplicates = new ArrayList<>(options);
		duplicates.sort((o1, o2) -> {
			if (o1 instanceof ProvidedModOption) {
				if (!(o2 instanceof ProvidedModOption)) {
					return 1;
				}
			} else {
				if (o2 instanceof ProvidedModOption) {
					return -1;
				}
			}
			if (o1 instanceof ModLoadOption && o2 instanceof ModLoadOption) {
				int comp = ((ModLoadOption) o1).metadata().name().compareTo(((ModLoadOption) o2).metadata().name());
				if (comp == 0) {
					comp = ((ModLoadOption) o1).version().compareTo(((ModLoadOption) o2).version());
				}

				return comp;
			}

			return LoadOption.COMPARATOR.compare(o1, o2);
		});
		return duplicates;
	}
}
