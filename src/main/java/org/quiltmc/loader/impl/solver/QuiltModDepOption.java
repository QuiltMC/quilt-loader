package org.quiltmc.loader.impl.solver;

import org.quiltmc.loader.api.ModDependency;

/** Used to indicate part of a {@link ModDependency} from quilt.mod.json. */
public class QuiltModDepOption extends LoadOption {
	public final ModDependency dep;

	public QuiltModDepOption(ModDependency dep) {
		this.dep = dep;
	}

	@Override
	public String toString() {
		return "QuiltModDep { " + dep + " }";
	}
}
