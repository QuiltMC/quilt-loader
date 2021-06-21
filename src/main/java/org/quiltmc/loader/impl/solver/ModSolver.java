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
import org.quiltmc.loader.impl.solver.ModSolveResult.LoadOptionResult;
import org.quiltmc.loader.impl.util.SystemProperties;
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
	// TODO: Find a way to sort versions of mods by suggestions and conflicts (not crucial, though)
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
		boolean isAdvanced = false;
		// modCandidateMap doesn't contain provided mods, whereas fullCandidateMap does.
		Map<String, List<ModCandidate>> modCandidateMap = new HashMap<>();
		Map<String, List<ModCandidate>> fullCandidateMap = new HashMap<>();

		Map<String, ModCandidate> mandatoryMods = new HashMap<>();
		List<ModResolutionException> errors = new ArrayList<>();

		for (ModCandidateSet mcs : modCandidateSetMap.values()) {
			try {
				Collection<ModCandidate> s = mcs.toSortedSet();
				modCandidateMap.computeIfAbsent(mcs.getModId(), i -> new ArrayList<>()).addAll(s);
				fullCandidateMap.computeIfAbsent(mcs.getModId(), i -> new ArrayList<>()).addAll(s);
				for (String modProvide : mcs.getModProvides()) {
					fullCandidateMap.computeIfAbsent(modProvide, i -> new ArrayList<>()).addAll(s);
				}
				isAdvanced |= (s.size() > 1) || (s.iterator().next().getDepth() > 0);

				for (ModCandidate c : s) {
					isAdvanced |= !c.getInfo().getProvides().isEmpty();
				}

				if (mcs.isUserProvided()) {
					mandatoryMods.put(mcs.getModId(), s.iterator().next());
				}
			} catch (ModResolutionException e) {
				// Only thrown by "ModCandidateSet.toSortedSet" when there are duplicate mandatory mods.
				// We collect them in a list so we can display all of the errors.
				errors.add(e);
			}
		}

		if (!errors.isEmpty()) {
			if (errors.size() == 1) {
				throw errors.get(0);
			}
			ModResolutionException ex = new ModResolutionException("Found " + errors.size() + " duplicated mandatory mods!");
			for (ModResolutionException error : errors) {
				ex.addSuppressed(error);
			}
			throw ex;
		}

		Map<String, ModCandidate> resultingModMap;
		Map<String, ModCandidate> providedModMap;
		Map<Class<? extends LoadOption>, LoadOptionResult<?>> extraResults;

		isAdvanced = true; // TODO: Weirdo hardsetting?

		if (!isAdvanced) {
			resultingModMap = new HashMap<>();
			providedModMap = new HashMap<>();
			for (Map.Entry<String, List<ModCandidate>> entry : modCandidateMap.entrySet()) {
				ModCandidate candidate = entry.getValue().iterator().next();
				resultingModMap.put(entry.getKey(), candidate);
				for (String provided : candidate.getInfo().getProvides()) {
					providedModMap.put(provided, candidate);
				}
			}
			extraResults = Collections.emptyMap();
		} else {
			Map<String, ModIdDefinition> modDefs = new HashMap<>();
			DependencyHelper<LoadOption, ModLink> helper = new DependencyHelper<>(org.quiltmc.loader.util.sat4j.pb.SolverFactory.newLight());
			helper.setNegator(new LoadOptionNegator());

			try {
				Map<String, List<ModLoadOption>> modOptions = new HashMap<>();
				Map<ModCandidate, MainModLoadOption> modToLoadOption = new HashMap<>();

				// Put primary mod (first mod in jar)
				for (Entry<String, List<ModCandidate>> entry : modCandidateMap.entrySet()) {
					String modId = entry.getKey();
					List<ModCandidate> candidates = entry.getValue();
					ModCandidate mandatedCandidate = mandatoryMods.get(modId);
					List<ModLoadOption> cOptions = modOptions.computeIfAbsent(modId, s -> new ArrayList<>());

					int index = 0;

					for (ModCandidate m : candidates) {
						MainModLoadOption cOption;

						if (m == mandatedCandidate) {
							cOption = new MainModLoadOption(mandatedCandidate, -1);
							modToLoadOption.put(mandatedCandidate, cOption);
							modDefs.put(modId, new MandatoryModIdDefinition(cOption));
						} else {
							cOption = new MainModLoadOption(m, candidates.size() == 1 ? -1 : index);
							modToLoadOption.put(m, cOption);
							helper.addToObjectiveFunction(cOption, -1000 + index++);
						}

						cOptions.add(cOption);

						for (String provided : m.getInfo().getProvides()) {
							// Add provided mods as an available option for other dependencies to select from.
							modOptions.computeIfAbsent(provided, s -> new ArrayList<>())
								.add(new ProvidedModOption(cOption, provided));
						}
					}
				}

				for (Entry<String, List<ModLoadOption>> entry : modOptions.entrySet()) {

					String modId = entry.getKey();
					ModIdDefinition def;
					ModLoadOption[] optionArray = entry.getValue().toArray(new ModLoadOption[0]);

					ModCandidate mandatoryCandidate = mandatoryMods.get(modId);

					if (mandatoryCandidate != null) {
						MandatoryModIdDefinition mandatoryDef = (MandatoryModIdDefinition) modDefs.get(modId);
						def = mandatoryDef;
						if (optionArray.length > 1) {
							def = new OverridenModIdDefintion(mandatoryDef, optionArray);
							mandatoryDef.put(helper);
						}
					} else {
						def = new OptionalModIdDefintion(modId, optionArray);
					}

					def.put(helper);
					modDefs.put(modId, def);
				}

				// Put dependencies and conflicts of everything
				for (Entry<ModCandidate, MainModLoadOption> entry : modToLoadOption.entrySet()) {
					ModCandidate mc = entry.getKey();
					MainModLoadOption option = entry.getValue();

					processDependencies(modDefs, helper, mc, option);
				}

			} catch (ContradictionException e) {
				// This shouldn't happen. But if it does it's a bit of a problem.
				throw new ModResolutionException(e);
			}

			// Resolving

			try {
				while (!helper.hasASolution()) {

					List<ModLink> why = new ArrayList<>(helper.why());

					Map<MainModLoadOption, MandatoryModIdDefinition> roots = new HashMap<>();
					List<ModLink> causes = new ArrayList<>();
					causes.addAll(why);

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

					ModResolutionException ex = describeError(roots, causes);
					if (ex == null) {
						ex = fallbackErrorDescription(roots, causes);
					}

					errors.add(ex);

					if (causes.isEmpty()) {
						break;
					} else {

						boolean removedAny = false;

						// Remove dependences and conflicts first
						for (ModLink link : causes) {

							if (link instanceof FabricModDependencyLink) {
								FabricModDependencyLink dep = (FabricModDependencyLink) link;

								if (!dep.validOptions.isEmpty()) {
									continue;
								}
							}

							if (link instanceof FabricModDependencyLink || link instanceof FabricModBreakLink) {
								if (helper.quilt_removeConstraint(link)) {
									removedAny = true;
									break;
								}
							}
						}

						// If that failed... try removing anything else
						if (!removedAny) {
							for (ModLink link : causes) {
								if (helper.quilt_removeConstraint(link)) {
									removedAny = true;
									break;
								}
							}
						}

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
					ModResolutionException ex = new ModResolutionException("Found " + errors.size() + " errors while resolving mods!");
					for (ModResolutionException error : errors) {
						ex.addSuppressed(error);
					}
					throw ex;
				}

			} catch (TimeoutException e) {
				throw new ModResolutionException("Mod collection took too long to be resolved", e);
			}

			Collection<LoadOption> solution = helper.getASolution();
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
							throw new ModResolutionException("Duplicate result ModCandidate for " + modOption.modId() + " - something has gone wrong internally!");
						}

						if (providedModMap.containsKey(modOption.modId())) {
							throw new ModResolutionException(modOption.modId() + " is already provided by " + providedModMap.get(modOption.modId())
									+ " - something has gone wrong internally!");
						}

						for (String provided : modOption.candidate.getInfo().getProvides()) {

							if (resultingModMap.containsKey(provided)) {
								throw new ModResolutionException(provided + " is already provided by " + resultingModMap.get(provided)
										+ " - something has gone wrong internally!");
							}

							previous = providedModMap.put(provided, modOption.candidate);
							if (previous != null) {
								throw new ModResolutionException("Duplicate provided ModCandidate for " + provided + " - something has gone wrong internally!");
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
		}

		// verify result: all mandatory mods
		Set<String> missingMods = new HashSet<>();
		for (String m : mandatoryMods.keySet()) {
			if (!resultingModMap.keySet().contains(m)) {
				missingMods.add(m);
			}
		}

		StringBuilder errorsHard = new StringBuilder();
		StringBuilder errorsSoft = new StringBuilder();

		// TODO: Convert to new error syntax
		if (!missingMods.isEmpty()) {
			errorsHard.append("\n - Missing mods: ").append(String.join(", ", missingMods));
		} else {
			// verify result: dependencies
			for (ModCandidate candidate : resultingModMap.values()) {
				for (ModDependency dependency : candidate.getInfo().getDepends()) {
					addErrorToList(candidate, dependency, resultingModMap, providedModMap, errorsHard, "requires", true);
				}

				for (ModDependency dependency : candidate.getInfo().getRecommends()) {
					addErrorToList(candidate, dependency, resultingModMap, providedModMap, errorsSoft, "recommends", true);
				}

				for (ModDependency dependency : candidate.getInfo().getBreaks()) {
					addErrorToList(candidate, dependency, resultingModMap, providedModMap, errorsHard, "is incompatible with", false);
				}

				for (ModDependency dependency : candidate.getInfo().getConflicts()) {
					addErrorToList(candidate, dependency, resultingModMap, providedModMap, errorsSoft, "conflicts with", false);
				}

				Version version = candidate.getInfo().getVersion();
				List<Version> suspiciousVersions = new ArrayList<>();

				for (ModCandidate other : fullCandidateMap.get(candidate.getInfo().getId())) {
					Version otherVersion = other.getInfo().getVersion();
					if (version instanceof Comparable && otherVersion instanceof Comparable && !version.equals(otherVersion)) {
						@SuppressWarnings("unchecked")
						Comparable<? super Version> cv = (Comparable<? super Version>) version;
						if (cv.compareTo(otherVersion) == 0) {
							suspiciousVersions.add(otherVersion);
						}
					}
				}

				if (!suspiciousVersions.isEmpty()) {
					errorsSoft.append("\n - Conflicting versions found for ")
						.append(candidate.getInfo().getId())
						.append(": used ")
						.append(version.getFriendlyString())
						.append(", also found ")
						.append(suspiciousVersions.stream().map(Version::getFriendlyString).collect(Collectors.joining(", ")));
				}
			}
		}

		// print errors
		String errHardStr = errorsHard.toString();
		String errSoftStr = errorsSoft.toString();

		if (!errSoftStr.isEmpty()) {
			logger.warn("Warnings were found! " + errSoftStr);
		}

		if (!errHardStr.isEmpty()) {
			throw new ModResolutionException("Errors were found!" + errHardStr + errSoftStr);
		}

		return new ModSolveResult(resultingModMap, providedModMap, extraResults);
	}

	private <O extends LoadOption> LoadOptionResult<O> createLoadOptionResult(Class<O> cls, Map<LoadOption, Boolean> map) {
        Map<O, Boolean> resultMap = new HashMap<>();
        for (Entry<LoadOption, Boolean> entry : map.entrySet()) {
            resultMap.put(cls.cast(entry.getKey()), entry.getValue());
        }
        return new LoadOptionResult<>(Collections.unmodifiableMap(resultMap));
	}

    void processDependencies(Map<String, ModIdDefinition> modDefs, DependencyHelper<LoadOption,
		ModLink> helper, ModCandidate mc, ModLoadOption option)
		throws ContradictionException {

		for (ModDependency dep : mc.getInfo().getDepends()) {
			ModIdDefinition def = modDefs.get(dep.getModId());
			if (def == null) {
				def = new OptionalModIdDefintion(dep.getModId(), new ModLoadOption[0]);
				modDefs.put(dep.getModId(), def);
				def.put(helper);
			}

			new FabricModDependencyLink(logger, option, dep, def).put(helper);
		}

		for (ModDependency conflict : mc.getInfo().getBreaks()) {
			ModIdDefinition def = modDefs.get(conflict.getModId());
			if (def == null) {
				def = new OptionalModIdDefintion(conflict.getModId(), new ModLoadOption[0]);
				modDefs.put(conflict.getModId(), def);

				def.put(helper);
			}

			new FabricModBreakLink(logger, option, conflict, def).put(helper);
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

	private static String getCandidateName(ModCandidate candidate) {
		return "'" + candidate.getInfo().getName() + "' (" + candidate.getInfo().getId() + ")";
	}

	private static String getCandidateFriendlyVersion(ModCandidate candidate) {
		return candidate.getInfo().getVersion().getFriendlyString();
	}

	private static String getDependencyVersionRequirements(ModDependency dependency) {
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
	private static ModResolutionException describeError(Map<MainModLoadOption, MandatoryModIdDefinition> roots, List<ModLink> causes) {
		// TODO: Create a graph from roots to each other and then build the error through that!
		return null;
	}

	private static ModResolutionException fallbackErrorDescription(Map<MainModLoadOption, MandatoryModIdDefinition> roots, List<ModLink> causes) {
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

			if (cause instanceof FabricModDependencyLink) {
				FabricModDependencyLink dep = (FabricModDependencyLink) cause;
				errors.append(dep.validOptions.isEmpty() ? "x" : "-");
				errors.append(" Mod ").append(getLoadOptionDescription(dep.source))
						.append(" requires ").append(getDependencyVersionRequirements(dep.publicDep))
						.append(" of ");
				ModIdDefinition def = dep.on;
				ModLoadOption[] sources = def.sources();

				if (sources.length == 0) {
					errors.append("unknown mod '").append(def.getModId()).append("'\n")
							.append("\t- You must install ").append(getDependencyVersionRequirements(dep.publicDep))
							.append(" of '").append(def.getModId()).append("'.");
				} else {
					errors.append(def.getFriendlyName());

					if (dep.validOptions.isEmpty()) {
						errors.append("\n\t- You must install ").append(getDependencyVersionRequirements(dep.publicDep))
								.append(" of ").append(def.getFriendlyName()).append('.');
					}

					if (sources.length == 1) {
						errors.append("\n\t- Your current version of ").append(getCandidateName(sources[0].candidate))
							.append(" is ").append(getCandidateFriendlyVersion(sources[0].candidate)).append(".");
					} else {
						errors.append("\n\t- You have the following versions available:");

						for (ModLoadOption source : sources) {
							errors.append("\n\t\t- ").append(getCandidateFriendlyVersion(source)).append(".");
						}
					}
				}
			} else if (cause instanceof FabricModBreakLink) {
				FabricModBreakLink breakage = (FabricModBreakLink) cause;
				errors.append(breakage.invalidOptions.isEmpty() ? "-" : "x");
				errors.append(" Mod ").append(getLoadOptionDescription(breakage.source))
						.append(" conflicts with ").append(getDependencyVersionRequirements(breakage.publicDep))
						.append(" of ");

				ModIdDefinition def = breakage.with;
				ModLoadOption[] sources = def.sources();

				if (sources.length == 0) {
					errors.append("unknown mod '").append(def.getModId()).append("'\n")
							.append("\t- You must remove ").append(getDependencyVersionRequirements(breakage.publicDep))
							.append(" of '").append(def.getModId()).append("'.");
				} else {
					errors.append(def.getFriendlyName());

					if (breakage.invalidOptions.isEmpty()) {
						errors.append("\n\t- You must remove ").append(getDependencyVersionRequirements(breakage.publicDep))
								.append(" of ").append(def.getFriendlyName()).append('.');
					}

					if (sources.length == 1) {
						errors.append("\n\t- Your current version of ").append(getCandidateName(sources[0].candidate))
							.append(" is ").append(getCandidateFriendlyVersion(sources[0].candidate)).append(".");
					} else {
						errors.append("\n\t- You have the following versions available:");

						for (ModLoadOption source : sources) {
							errors.append("\n\t\t- ").append(getCandidateFriendlyVersion(source)).append(".");
						}
					}
				}
			} else {
				errors.append("x Unknown error type?")
						.append("\n\t+ cause.getClass() =>")
						.append("\n\t\t").append(cause.getClass().getName())
						.append("\n\t+ cause.toString() =>")
						.append("\n\t\t").append(cause.toString());
			}
		}

		// TODO: See if I can get results similar to appendJiJInfo (which requires a complete "mod ID -> candidate" map)
		HashSet<String> listedSources = new HashSet<>();
		for (ModLoadOption involvedMod : roots.keySet()) {
			appendLoadSourceInfo(errors, listedSources, involvedMod);
		}

		for (ModLink involvedLink : causes) {
			if (involvedLink instanceof FabricModDependencyLink) {
				appendLoadSourceInfo(errors, listedSources, ((FabricModDependencyLink) involvedLink).on);
			} else if (involvedLink instanceof FabricModBreakLink) {
				appendLoadSourceInfo(errors, listedSources, ((FabricModBreakLink) involvedLink).with);
			}
		}

		return new ModResolutionException(errors.toString());
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

	private static String getLoadOptionDescription(ModLoadOption loadOption) {
		return getCandidateName(loadOption) + " v" + getCandidateFriendlyVersion(loadOption);
	}

	static String getCandidateName(ModLoadOption candidate) {
		return getCandidateName(candidate.candidate);
	}

	private static String getCandidateFriendlyVersion(ModLoadOption candidate) {
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
