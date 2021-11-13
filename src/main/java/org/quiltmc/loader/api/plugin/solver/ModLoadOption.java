package org.quiltmc.loader.api.plugin.solver;

import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.plugin.FullModMetadata;
import org.quiltmc.loader.api.plugin.ModCandidate;

public interface ModLoadOption {

	/** @return The real candidate for this mod. Note that the candidates metadata might not be accurate, so you should
	 *         always use {@link #metadata()} instead. (In particular the {@link ModLoadOption} for provided mods will
	 *         return the candidate that is providing it, but different metadata). */
	ModCandidate candidate();

	default FullModMetadata metadata() {
		return candidate().metadata();
	}

	default String group() {
		return metadata().group();
	}

	default String id() {
		return metadata().id();
	}

	default Version version() {
		return metadata().version();
	}
}
