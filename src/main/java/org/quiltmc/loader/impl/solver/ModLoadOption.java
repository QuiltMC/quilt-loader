package org.quiltmc.loader.impl.solver;

import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.discovery.ModCandidate;
import org.quiltmc.loader.impl.discovery.ModResolver;

abstract class ModLoadOption extends LoadOption {
	final ModCandidate candidate;

	ModLoadOption(ModCandidate candidate) {
		this.candidate = candidate;
	}

	String getSourceIcon() {
		// TODO: Base this on whether the candidate was loaded from a fabric.mod.json or quilt.mod.json
		return "$jar+fabric$";
	}

	String modId() {
		return candidate.getInfo().getId();
	}

	@Override
	public String toString() {
		return shortString();
	}
	
	abstract String shortString();

	String fullString() {
		return shortString() + " " + getSpecificInfo();
	}

	String getLoadSource() {
		return ModResolver.getReadablePath(QuiltLoaderImpl.INSTANCE, candidate);
	}

	abstract String getSpecificInfo();

	abstract MainModLoadOption getRoot();
}
