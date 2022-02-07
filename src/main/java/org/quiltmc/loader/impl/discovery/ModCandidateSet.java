package org.quiltmc.loader.impl.discovery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.metadata.qmj.ModProvided;

import net.fabricmc.loader.api.Version;

public class ModCandidateSet {
	private final String modId;
	private final List<String> modProvides = new ArrayList<>();
	private final Set<ModCandidate> depthZeroCandidates = new HashSet<>();
	private final Map<String, ModCandidate> candidates = new HashMap<>();

	private static int compare(ModCandidate a, ModCandidate b) {
		Version av = a.getInfo().getVersion();
		Version bv = b.getInfo().getVersion();

		if (av != null && bv != null) {
			return bv.compareTo(av);
		} else {
			return 0;
		}
	}

	public ModCandidateSet(String modId) {
		this.modId = modId;
	}

	public String getModId() {
		return modId;
	}

	public List<String> getModProvides() {
		return modProvides;
	}

	public synchronized boolean add(ModCandidate candidate) {
		String version = candidate.getInfo().getVersion().getFriendlyString();
		ModCandidate oldCandidate = candidates.get(version);
		if (oldCandidate != null) {
			int oldDepth = oldCandidate.getDepth();
			int newDepth = candidate.getDepth();

			if (oldDepth <= newDepth) {
				return false;
			} else {
				candidates.remove(version);
				if (oldDepth > 0) {
					depthZeroCandidates.remove(oldCandidate);
				}
			}
		}

		candidates.put(version, candidate);
		for (ModProvided provided : candidate.getMetadata().provides()) {
			modProvides.add(provided.id);
		}
		if (candidate.getDepth() == 0) {
			depthZeroCandidates.add(candidate);
		}

		return true;
	}

	public boolean isUserProvided() {
		return !depthZeroCandidates.isEmpty();
	}

	public Collection<ModCandidate> toSortedSet() throws ModSolvingException {
		if (depthZeroCandidates.size() > 1) {
			StringBuilder sb = new StringBuilder("Duplicate mandatory mods found for '" + modId + "':");
			for (ModCandidate mc : depthZeroCandidates) {
				sb.append("\n").append(mc.getInfo().getVersion()).append(" from ").append(ModResolver.getReadablePath(QuiltLoaderImpl.INSTANCE, mc));
			}
			throw new ModSolvingException(sb.toString());
		} else if (candidates.size() > 1) {
			List<ModCandidate> out = new ArrayList<>(candidates.values());
			out.sort(ModCandidateSet::compare);
			return out;
		} else {
			return Collections.singleton(candidates.values().iterator().next());
		}
	}
}
