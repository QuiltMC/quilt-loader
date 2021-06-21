package org.quiltmc.loader.impl.solver;

/**
 * A mod that is provided from the jar of a different mod.
 */
class ProvidedModOption extends ModLoadOption implements AliasedLoadOption {
	final MainModLoadOption provider;
	final String providedModId;

	public ProvidedModOption(MainModLoadOption provider, String providedModId) {
		super(provider.candidate);
		this.provider = provider;
		this.providedModId = providedModId;
	}

	@Override
	String modId() {
		return providedModId;
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
