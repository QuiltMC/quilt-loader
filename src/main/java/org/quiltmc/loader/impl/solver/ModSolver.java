package org.quiltmc.loader.impl.solver;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Logger;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.discovery.ModCandidate;
import org.quiltmc.loader.impl.discovery.ModCandidateSet;
import org.quiltmc.loader.impl.discovery.ModResolutionException;
import org.quiltmc.loader.impl.discovery.ModResolver;
import org.quiltmc.loader.impl.discovery.ModSolvingError;
import org.quiltmc.loader.impl.discovery.ModSolvingException;
import org.quiltmc.loader.impl.metadata.qmj.ModLoadType;
import org.quiltmc.loader.impl.metadata.qmj.ModProvided;
import org.quiltmc.loader.impl.solver.ModSolveResult.LoadOptionResult;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.util.sat4j.pb.IPBSolver;
import org.quiltmc.loader.util.sat4j.pb.OptToPBSATAdapter;
import org.quiltmc.loader.util.sat4j.pb.PseudoOptDecorator;
import org.quiltmc.loader.util.sat4j.pb.tools.DependencyHelper;
import org.quiltmc.loader.util.sat4j.pb.tools.INegator;
import org.quiltmc.loader.util.sat4j.specs.ContradictionException;
import org.quiltmc.loader.util.sat4j.specs.TimeoutException;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModDependency;

public final class ModSolver {
	static final boolean DEBUG_PRINT_STATE = Boolean.getBoolean(SystemProperties.DEBUG_MOD_RESOLVING);

	private final Logger logger;

	public ModSolver(Logger logger) {
		this.logger = logger;
	}

	/** Primarily used by {@link ModResolver#resolve(QuiltLoaderImpl)} to find a valid map of mod ids to a single mod candidate, where
	 * all of the dependencies are present and no "breaking" mods are present.
	 * 
	 * @return A valid list of mods.
	 * @throws ModResolutionException if that is impossible. */
	public ModSolveResult findCompatibleSet(Map<String, ModCandidateSet> modCandidateSetMap) throws ModResolutionException {

		/*
		 * Implementation notes:
		 *
		 * This makes heavy use of the Sat4j "partial boolean" functionality.
		 *
		 * To make defining mod [TODO]
		 */

		// First, map all ModCandidateSets to Set<ModCandidate>s.

		/* This step performs the following actions:
		 * 
		 * 1: Checks to see if there are duplicate "mandatory" mods. (A mandatory mod is one where the user has
		 *	 added it directly to their mods folder or classpath, and duplicates would indicate that they
		 *	 have added multiple - E.G. both "buildcraft-9.0.1.jar" and "buildcraft-9.0.2.jar" are present).
		 *
		 * 2: Sorts all available instances of mods by their version - this means that we try to load the newest
		 *	 valid version of non-mandatory mods (I.E. library mods).
		 * 
		 * 3: Determines if we need to use sat4j at all - in simple cases (no jar-in-jar mods, and no "optional" mods)
		 *	 the valid mod list is just the list of mods available. Or, if there are missing dependencies or
		 *	 present "breaking" mods then we only need to perform the validation at the end of resolving.
		 */
		// modCandidateMap doesn't contain provided mods, whereas fullCandidateMap does.
		Map<String, List<ModCandidate>> modCandidateMap = new HashMap<>();
		Map<String, List<ModCandidate>> fullCandidateMap = new HashMap<>();

		Map<String, ModCandidate> mandatoryMods = new HashMap<>();
		List<ModSolvingException> errors = new ArrayList<>();

		for (ModCandidateSet mcs : modCandidateSetMap.values()) {
			try {
				Collection<ModCandidate> s = mcs.toSortedSet();
				modCandidateMap.computeIfAbsent(mcs.getModId(), i -> new ArrayList<>()).addAll(s);
				fullCandidateMap.computeIfAbsent(mcs.getModId(), i -> new ArrayList<>()).addAll(s);
				for (String modProvide : mcs.getModProvides()) {
					fullCandidateMap.computeIfAbsent(modProvide, i -> new ArrayList<>()).addAll(s);
				}

				if (mcs.isUserProvided()) {
					mandatoryMods.put(mcs.getModId(), s.iterator().next());
				}
			} catch (ModSolvingException e) {
				// Only thrown by "ModCandidateSet.toSortedSet" when there are duplicate mandatory mods.
				// We collect them in a list so we can display all of the errors.
				errors.add(e);
			}
		}

		if (!errors.isEmpty()) {
			if (errors.size() == 1) {
				throw errors.get(0);
			}
			ModSolvingException ex = new ModSolvingException("Found " + errors.size() + " duplicated mandatory mods!");
			for (ModSolvingException error : errors) {
				ex.addSuppressed(error);
			}
			throw ex;
		}

		Map<String, ModCandidate> resultingModMap;
		Map<String, ModCandidate> providedModMap;
		Map<Class<? extends LoadOption>, LoadOptionResult<?>> extraResults;

		Sat4jWrapper sat = new Sat4jWrapper();
		Map<String, OptionalModIdDefintion> modDefs = new HashMap<>();

		// Put primary mod (first mod in jar)
		for (Entry<String, List<ModCandidate>> entry : modCandidateMap.entrySet()) {
			String modId = entry.getKey();
			List<ModCandidate> candidates = entry.getValue();
			ModCandidate mandatedCandidate = mandatoryMods.get(modId);

			int index = 0;

			for (ModCandidate m : candidates) {
				MainModLoadOption cOption;

				if (m == mandatedCandidate) {
					cOption = new MainModLoadOption(mandatedCandidate, -1);

					sat.addOption(cOption);
					sat.addRule(new MandatoryModIdDefinition(cOption));

				} else {
					cOption = new MainModLoadOption(m, candidates.size() == 1 ? -1 : index);
					sat.addOption(cOption);

					if (!modDefs.containsKey(modId)) {
						OptionalModIdDefintion def = new OptionalModIdDefintion(modId);
						modDefs.put(modId, def);
						sat.addRule(def);
					}

					// IF_REQUIRED uses a positive weight to discourage it from being chosen
					// IF_POSSIBLE uses a negative weight to encourage it to be chosen
					// ALWAYS is handled directly in OptionalModIdDefinition
					int weight = 1000;
					if (m.getMetadata().loadType() == ModLoadType.IF_POSSIBLE) {
						weight = -weight;
					}
					// Always prefer newer versions
					weight += index++;
					sat.setWeight(cOption, weight);
				}

				for (ModProvided provided : m.getMetadata().provides()) {
					// Add provided mods as an available option for other dependencies to select from.
					sat.addOption(new ProvidedModOption(cOption, provided));
				}

				for (org.quiltmc.loader.api.ModDependency dep : m.getMetadata().depends()) {

					if (dep.shouldIgnore()) {
						continue;
					}

					sat.addRule(createModDepLink(logger, sat, cOption, dep));
				}

				for (org.quiltmc.loader.api.ModDependency dep : m.getMetadata().breaks()) {

					if (dep.shouldIgnore()) {
						continue;
					}

					sat.addRule(createModBreaks(logger, sat, cOption, dep));
				}
			}
		}

		// Resolving

		try {
			while (!sat.hasSolution()) {

				Collection<ModLink> why = sat.getError();

				Map<MainModLoadOption, MandatoryModIdDefinition> roots = new HashMap<>();
				List<ModLink> causes = new ArrayList<>();
				for (ModLink link : why) {
					if (!causes.contains(link)) {
						causes.add(link);
					}
				}

				// Separate out mandatory mods (roots) from other causes
				for (Iterator<ModLink> iterator = causes.iterator(); iterator.hasNext();) {
					ModLink link = iterator.next();
					if (link instanceof MandatoryModIdDefinition) {
						MandatoryModIdDefinition mandatoryMod = (MandatoryModIdDefinition) link;
						roots.put(mandatoryMod.candidate, mandatoryMod);
						iterator.remove();
					}
				}

				// Plugin functionality to handle errors would likely go *RIGHT HERE*
				// I.E. downloading missing / outdated *optional* mods

				/*
				 * Plugins would most likely be passed the map of roots, and the list of causes.
				 * Alternatively, we could do some pre-processing to identify specific cases, such as
				 * a mod or library (or api class implementation) being missing, and then send those
				 * out separately.
				 *
				 * This does mean that we'd need to remove and re-add edited clauses though when
				 * trying to fix problems. (For example downloading a mod should process all of the
				 * dependencies, provides, etc of that mod related to all others). 
				 */

				ModSolvingException ex = describeError(roots, causes);
				if (ex == null) {
					ex = fallbackErrorDescription(roots, causes);
				}

				errors.add(ex);

				if (causes.isEmpty()) {
					break;
				} else {

					boolean removedAny = blameSingleRule(sat, causes);

					// If that failed... stop finding more errors
					if (!removedAny) {
						break;
					}
				}
			}

			if (!errors.isEmpty()) {
				if (errors.size() == 1) {
					throw errors.get(0);
				}
				ModSolvingException ex = new ModSolvingException("Found " + errors.size() + " errors while resolving mods!");
				for (ModSolvingException error : errors) {
					ex.addSuppressed(error);
				}
				throw ex;
			}

		} catch (TimeoutException e) {
			throw new ModSolvingError("Mod collection took too long to be resolved", e);
		}

		Collection<LoadOption> solution;
		try {
			solution = sat.getSolution();
		} catch (TimeoutException e) {
			throw new ModSolvingError("Mod collection took too long to be optimised", e);
		}

		resultingModMap = new HashMap<>();
		providedModMap = new HashMap<>();

		Map<Class<? extends LoadOption>, Map<LoadOption, Boolean>> optionMap = new HashMap<>();

		for (LoadOption option : solution) {

			boolean negated = option instanceof NegatedLoadOption;
			if (negated) {
				option = ((NegatedLoadOption) option).not;
			}

			Class<?> cls = option.getClass();
			do {
				optionMap.computeIfAbsent(cls.asSubclass(LoadOption.class), c -> new HashMap<>()).put(option, !negated);
			} while (LoadOption.class.isAssignableFrom((cls = cls.getSuperclass())));

			if (option instanceof ModLoadOption) {
				if (!negated) {
					ModLoadOption modOption = (ModLoadOption) option;

					ModCandidate previous = resultingModMap.put(modOption.modId(), modOption.candidate);
					if (previous != null) {
						throw new ModSolvingError("Duplicate result ModCandidate for " + modOption.modId() + " - something has gone wrong internally!");
					}

					if (providedModMap.containsKey(modOption.modId())) {
						throw new ModSolvingError(modOption.modId() + " is already provided by " + providedModMap.get(modOption.modId())
								+ " - something has gone wrong internally!");
					}

					for (ModProvided provided : modOption.candidate.getMetadata().provides()) {

						if (resultingModMap.containsKey(provided.id)) {
							throw new ModSolvingError(provided + " is already provided by " + resultingModMap.get(provided.id)
									+ " - something has gone wrong internally!");
						}

						previous = providedModMap.put(provided.id, modOption.candidate);
						if (previous != null) {
							throw new ModSolvingError("Duplicate provided ModCandidate for " + provided + " - something has gone wrong internally!");
						}
					}
				}
			}
		}

		extraResults = new HashMap<>();

		for (Map.Entry<Class<? extends LoadOption>, Map<LoadOption, Boolean>> entry : optionMap.entrySet()) {
			Class<? extends LoadOption> cls = entry.getKey();
			Map<LoadOption, Boolean> map = entry.getValue();
			extraResults.put(cls, createLoadOptionResult(cls, map));
		}
		extraResults = Collections.unmodifiableMap(extraResults);

		// TODO: Warn on suspiciously similar versions!

		return new ModSolveResult(resultingModMap, providedModMap, extraResults);
	}

	private boolean blameSingleRule(Sat4jWrapper sat, List<ModLink> causes) {

		// Remove dependencies and conflicts first
		for (ModLink link : causes) {

			if (link instanceof QuiltModLinkDep) {
				QuiltModLinkDep dep = (QuiltModLinkDep) link;

				if (dep.hasAnyValidOptions()) {
					continue;
				}
			}

			sat.removeRule(link);
			return true;
		}

		// If that failed... try removing anything else
		for (ModLink link : causes) {
			sat.removeRule(link);
			return true;
		}

		return false;
	}

	private <O extends LoadOption> LoadOptionResult<O> createLoadOptionResult(Class<O> cls, Map<LoadOption, Boolean> map) {
		Map<O, Boolean> resultMap = new HashMap<>();
		for (Entry<LoadOption, Boolean> entry : map.entrySet()) {
			resultMap.put(cls.cast(entry.getKey()), entry.getValue());
		}
		return new LoadOptionResult<>(Collections.unmodifiableMap(resultMap));
	}

	public static QuiltModLinkDep createModDepLink(Logger logger, RuleContext ctx, LoadOption option, org.quiltmc.loader.api.ModDependency dep) {

		if (dep instanceof org.quiltmc.loader.api.ModDependency.Any) {
			org.quiltmc.loader.api.ModDependency.Any any = (org.quiltmc.loader.api.ModDependency.Any) dep;

			return new QuiltModLinkDepAny(logger, ctx, option, any);
		} else {
			org.quiltmc.loader.api.ModDependency.Only only = (org.quiltmc.loader.api.ModDependency.Only) dep;

			return new QuiltModLinkDepOnly(logger, ctx, option, only);
		}
	}

	public static QuiltModLinkBreak createModBreaks(Logger logger, RuleContext ctx, LoadOption option, org.quiltmc.loader.api.ModDependency dep) {
		if (dep instanceof org.quiltmc.loader.api.ModDependency.All) {
			org.quiltmc.loader.api.ModDependency.All any = (org.quiltmc.loader.api.ModDependency.All) dep;

			return new QuiltModLinkBreakAll(logger, ctx, option, any);
		} else {
			org.quiltmc.loader.api.ModDependency.Only only = (org.quiltmc.loader.api.ModDependency.Only) dep;

			return new QuiltModLinkBreakOnly(logger, ctx, option, only);
		}
	}

	// TODO: Convert all these methods to new error syntax
	private void addErrorToList(ModCandidate candidate, ModDependency dependency, Map<String, ModCandidate> result, Map<String, ModCandidate> provided, StringBuilder errors, String errorType, boolean cond) {
		String depModId = dependency.getModId();

		List<String> errorList = new ArrayList<>();

		if (!isModIdValid(depModId, errorList)) {
			errors.append("\n - Mod ").append(getCandidateName(candidate)).append(" ").append(errorType).append(" ")
					.append(depModId).append(", which has an invalid mod ID because:");

			for (String error : errorList) {
				errors.append("\n\t - It ").append(error);
			}

			return;
		}

		ModCandidate depCandidate = result.get(depModId);
		// attempt searching provides
		if(depCandidate == null) {
			depCandidate = provided.get(depModId);
			if (depCandidate != null) {
				if(QuiltLoaderImpl.INSTANCE.isDevelopmentEnvironment()) {
					logger.warn("Mod " + candidate.getInfo().getId() + " is using the provided alias " + depModId + " in place of the real mod id " + depCandidate.getInfo().getId() + ".  Please use the mod id instead of a provided alias.");
				}
			}
		}
		boolean isPresent = depCandidate != null && dependency.matches(depCandidate.getInfo().getVersion());

		if (isPresent != cond) {
			errors.append("\n - Mod ").append(getCandidateName(candidate)).append(" ").append(errorType).append(" ")
					.append(getDependencyVersionRequirements(dependency)).append(" of mod ")
					.append(depCandidate == null ? depModId : getCandidateName(depCandidate)).append(", ");
			if (depCandidate == null) {
				appendMissingDependencyError(errors, dependency);
			} else if (cond) {
				appendUnsatisfiedDependencyError(errors, dependency, depCandidate);
			} else if (errorType.contains("conf")) {
				// CONFLICTS WITH
				appendConflictError(errors, candidate, depCandidate);
			} else {
				appendBreakingError(errors, candidate, depCandidate);
			}
			if (depCandidate != null) {
				appendJiJInfo(errors, result, depCandidate);
			}
		}
	}

	private void appendMissingDependencyError(StringBuilder errors, ModDependency dependency) {
		errors.append("which is missing!");
		errors.append("\n\t - You must install ").append(getDependencyVersionRequirements(dependency)).append(" of ")
				.append(dependency.getModId()).append(".");
	}

	private void appendUnsatisfiedDependencyError(StringBuilder errors, ModDependency dependency, ModCandidate depCandidate) {
		errors.append("but a non-matching version is present: ").append(getCandidateFriendlyVersion(depCandidate)).append("!");
		errors.append("\n\t - You must install ").append(getDependencyVersionRequirements(dependency)).append(" of ")
				.append(getCandidateName(depCandidate)).append(".");
	}

	private void appendConflictError(StringBuilder errors, ModCandidate candidate, ModCandidate depCandidate) {
		final String depCandidateVer = getCandidateFriendlyVersion(depCandidate);
		errors.append("but a matching version is present: ").append(depCandidateVer).append("!");
		errors.append("\n\t - While this won't prevent you from starting the game,");
		errors.append(" the developer(s) of ").append(getCandidateName(candidate));
		errors.append(" have found that version ").append(depCandidateVer).append(" of ").append(getCandidateName(depCandidate));
		errors.append(" conflicts with their mod.");
		errors.append("\n\t - It is heavily recommended to remove one of the mods.");
	}

	private void appendBreakingError(StringBuilder errors, ModCandidate candidate, ModCandidate depCandidate) {
		final String depCandidateVer = getCandidateFriendlyVersion(depCandidate);
		errors.append("but a matching version is present: ").append(depCandidate.getInfo().getVersion()).append("!");
		errors.append("\n\t - The developer(s) of ").append(getCandidateName(candidate));
		errors.append(" have found that version ").append(depCandidateVer).append(" of ").append(getCandidateName(depCandidate));
		errors.append(" critically conflicts with their mod.");
		errors.append("\n\t - You must remove one of the mods.");
	}

	private void appendJiJInfo(StringBuilder errors, Map<String, ModCandidate> result, ModCandidate candidate) {
		if (candidate.getDepth() < 1) {
			errors.append("\n\t - Mod ").append(getCandidateName(candidate))
					.append(" v").append(getCandidateFriendlyVersion(candidate))
					.append(" is being loaded from the user's mod directory.");
			return;
		}
		URL originUrl = candidate.getOriginUrl();
		// step 1: try to find source mod's URL
		URL sourceUrl = getSourceURL(originUrl);
		if (sourceUrl == null) {
			errors.append("\n\t - Mod ").append(getCandidateName(candidate))
					.append(" v").append(getCandidateFriendlyVersion(candidate))
					.append(" is being provided by <unknown mod>.");
			return;
		}
		// step 2: try to find source mod candidate
		ModCandidate srcCandidate = null;
		for (Map.Entry<String, ModCandidate> entry : result.entrySet()) {
			if (sourceUrl.equals(entry.getValue().getOriginUrl())) {
				srcCandidate = entry.getValue();
				break;
			}
		}
		if (srcCandidate == null) {
			errors.append("\n\t - Mod ").append(getCandidateName(candidate))
					.append(" v").append(getCandidateFriendlyVersion(candidate))
					.append(" is being provided by <unknown mod: ")
					.append(sourceUrl).append(">.");
			return;
		}
		// now we have the proper data, yay
		errors.append("\n\t - Mod ").append(getCandidateName(candidate))
				.append(" v").append(getCandidateFriendlyVersion(candidate))
				.append(" is being provided by ").append(getCandidateName(srcCandidate))
				.append(" v").append(getCandidateFriendlyVersion(candidate))
				.append('.');
	}

	private URL getSourceURL(URL originUrl) {
		return ModResolver.getSourceURL(originUrl);
	}

	static String getCandidateName(ModCandidate candidate) {
		return "'" + candidate.getInfo().getName() + "' (" + candidate.getInfo().getId() + ")";
	}

	static String getCandidateFriendlyVersion(ModCandidate candidate) {
		return candidate.getInfo().getVersion().getFriendlyString();
	}

	static String getDependencyVersionRequirements(ModDependency dependency) {
		return dependency.getVersionRequirements().stream().map(predicate -> {
			String version = predicate.getVersion();
			String[] parts;
			switch(predicate.getType()) {
			case ANY:
				return "any version";
			case EQUALS:
				return "version " + version;
			case GREATER_THAN:
				return "any version after " + version;
			case LESSER_THAN:
				return "any version before " + version;
			case GREATER_THAN_OR_EQUAL:
				return "version " + version + " or later";
			case LESSER_THAN_OR_EQUAL:
				return "version " + version + " or earlier";
			case SAME_MAJOR:
				parts = version.split("\\.");

				for (int i = 1; i < parts.length; i++) {
					parts[i] = "x";
				}

				return "version " + String.join(".", parts);
			case SAME_MAJOR_AND_MINOR:
				parts = version.split("\\.");

				for (int i = 2; i < parts.length; i++) {
					parts[i] = "x";
				}

				return "version " + String.join(".", parts);
			default:
				return "unknown version"; // should be unreachable
			}
		}).collect(Collectors.joining(" or "));
	}

	/** @param errorList The list of errors. The returned list of errors all need to be prefixed with "it " in order to make sense. */
	private static boolean isModIdValid(String modId, List<String> errorList) {
		return ModResolver.isModIdValid(modId, errorList);
	}

	/** @return A {@link ModResolutionException} describing the error in a readable format, or null if this is unable to
	 *		 do so. (In which case {@link #fallbackErrorDescription(Map, List)} will be used instead). */
	private static ModSolvingException describeError(Map<MainModLoadOption, MandatoryModIdDefinition> roots, List<ModLink> causes) {
		// TODO: Create a graph from roots to each other and then build the error through that!
		return null;
	}

	private static ModSolvingException fallbackErrorDescription(Map<MainModLoadOption, MandatoryModIdDefinition> roots, List<ModLink> causes) {
		StringBuilder errors = new StringBuilder("Unhandled error involving mod");

		if (roots.size() > 1) {
			errors.append('s');
		}

		errors.append(' ').append(roots.keySet().stream()
				.map(ModSolver::getLoadOptionDescription)
				.collect(Collectors.joining(", ")))
				.append(':');

		for (ModLink cause : causes) {
			errors.append('\n');

			cause.fallbackErrorDescription(errors);
		}

		// TODO: See if I can get results similar to appendJiJInfo (which requires a complete "mod ID -> candidate" map)
//		HashSet<String> listedSources = new HashSet<>();
//		for (ModLoadOption involvedMod : roots.keySet()) {
//			appendLoadSourceInfo(errors, listedSources, involvedMod);
//		}
//
//		for (ModLink involvedLink : causes) {
//			if (involvedLink instanceof FabricModDependencyLink) {
//				appendLoadSourceInfo(errors, listedSources, ((FabricModDependencyLink) involvedLink).on);
//			} else if (involvedLink instanceof FabricModBreakLink) {
//				appendLoadSourceInfo(errors, listedSources, ((FabricModBreakLink) involvedLink).with);
//			}
//		}

		return new ModSolvingException(errors.toString());
	}

	private static void appendLoadSourceInfo(StringBuilder errors, HashSet<String> listedSources, ModIdDefinition def) {
		if (!listedSources.add(def.getModId())) {
			return;
		}

		ModLoadOption[] sources = def.sources();

		if (sources.length == 0) {
			return;
		}

		if (sources.length == 1) {
			errors.append("\n- ").append(sources[0].getSourceIcon()).append(" ").append(getLoadOptionDescription(sources[0]))
				.append(" is being loaded from \"").append(sources[0].getLoadSource()).append("\".");
		} else {
			String name = getCandidateName(sources[0].candidate);
			for (ModLoadOption option : sources) {
				if (!getCandidateName(option.candidate).equals(name)) {
					name = null;
					break;
				}
			}

			if (name != null) {
				errors.append("\n- $folder$ ").append(name).append(" can be loaded from:");

				for (ModLoadOption source : sources) {
					errors.append("\n\t- ").append(source.getSourceIcon()).append(" v")
						.append(getCandidateFriendlyVersion(source))
						.append(" in \"").append(source.getLoadSource()).append("\".");
				}
			} else {
				errors.append("\n- $folder$ Mod ").append(def.getModId()).append(" can be loaded from:");

				for (ModLoadOption source : sources) {
					errors.append("\n\t- ").append(source.getSourceIcon()).append(" ")
						.append(getLoadOptionDescription(source))
						.append(" \"").append(source.getLoadSource()).append("\".");
				}
			}
		}
	}

	private static void appendLoadSourceInfo(StringBuilder errors, HashSet<String> listedSources, ModLoadOption option) {
		if (listedSources.add(option.modId())) {
			errors.append("\n- ").append(option.getSourceIcon()).append(" ")
					.append(getLoadOptionDescription(option))
					.append(" is being loaded from \"").append(option.getLoadSource()).append("\".");
		}
	}

	static String getLoadOptionDescription(ModLoadOption loadOption) {
		return getCandidateName(loadOption) + " v" + getCandidateFriendlyVersion(loadOption);
	}

	static String getCandidateName(ModLoadOption candidate) {
		return getCandidateName(candidate.candidate);
	}

	static String getCandidateFriendlyVersion(ModLoadOption candidate) {
		return getCandidateFriendlyVersion(candidate.candidate);
	}

	static final class LoadOptionNegator implements INegator {
		@Override
		public boolean isNegated(Object thing) {
			return thing instanceof NegatedLoadOption;
		}

		@Override
		public Object unNegate(Object thing) {
			return ((NegatedLoadOption) thing).not;
		}
	}
}
