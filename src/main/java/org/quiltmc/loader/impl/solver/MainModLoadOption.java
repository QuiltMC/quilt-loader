package org.quiltmc.loader.impl.solver;

import org.quiltmc.loader.impl.discovery.ModCandidate;
import org.quiltmc.loader.impl.metadata.LoaderModMetadata;

class MainModLoadOption extends ModLoadOption {
	/** Used to identify this {@link MainModLoadOption} against others with the same modid. A value of -1 indicates that
	 * this is the only {@link LoadOption} for the given modid. */
	final int index;

	MainModLoadOption(ModCandidate candidate, int index) {
		super(candidate);
		this.index = index;
	}

	@Override
	String shortString() {
		if (index == -1) {
			return "mod '" + modId() + "'";
		} else {
			return "mod '" + modId() + "'#" + (index + 1);
		}
	}

	@Override
	String getSpecificInfo() {
		LoaderModMetadata info = candidate.getInfo();
		return "version " + info.getVersion() + " loaded from " + getLoadSource();
	}

	@Override
	MainModLoadOption getRoot() {
		return this;
	}
}
