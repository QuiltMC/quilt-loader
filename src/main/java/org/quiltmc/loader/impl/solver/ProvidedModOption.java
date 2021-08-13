package org.quiltmc.loader.impl.solver;

import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.impl.metadata.qmj.ModProvided;

/**
 * A mod that is provided from the jar of a different mod.
 */
class ProvidedModOption extends ModLoadOption implements AliasedLoadOption {
	final MainModLoadOption provider;
	final ModProvided provided;

	public ProvidedModOption(MainModLoadOption provider, ModProvided provided) {
		super(provider.candidate);
		this.provider = provider;
		this.provided = provided;
	}

	@Override
	String group() {
		return provided.group.isEmpty() ? super.group() : provided.group;
	}

	@Override
	String modId() {
		return provided.id;
	}

	@Override
	Version version() {
		return provided.version;
	}

	@Override
	String shortString() {
		return "provided mod '" + modId() + "' from " + provider.shortString();
	}

	@Override
	String getSpecificInfo() {
		return provider.getSpecificInfo();
	}

	@Override
	MainModLoadOption getRoot() {
		return provider;
	}

	@Override
	public LoadOption getTarget() {
		return getRoot();
	}
}
