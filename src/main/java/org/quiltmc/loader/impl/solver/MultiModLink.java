package org.quiltmc.loader.impl.solver;

import java.util.Collection;
import java.util.Collections;

import org.quiltmc.loader.util.sat4j.pb.tools.DependencyHelper;
import org.quiltmc.loader.util.sat4j.specs.ContradictionException;

/** A {@link ModLink} that is part of a larger {@link ModLink}. This is needed since sat4j requires each clause to have
 * a different {@link ModLink} object. */
final class MultiModLink extends ModLink {
	final ModLink real;

	public MultiModLink(ModLink real) {
		super(real);
		this.real = real;
	}

	@Override
	ModLink put(DependencyHelper<LoadOption, ModLink> helper) throws ContradictionException {
		throw new UnsupportedOperationException("Don't put this!");
	}

	@Override
	public String toString() {
		return "Multi link " + real.multiLinks.indexOf(this) + " / " + real.multiLinks.size() + " for " + real;
	}

	@Override
	public Collection<? extends LoadOption> getNodesFrom() {
		return Collections.emptyList();
	}

	@Override
	public Collection<? extends LoadOption> getNodesTo() {
		return Collections.emptyList();
	}

	@Override
	protected int compareToSelf(ModLink o) {
		MultiModLink other = (MultiModLink) o;
		if (real == other.real) {
			return Integer.compare(real.multiLinks.indexOf(this), real.multiLinks.indexOf(other));
		} else {
			return real.compareTo(other.real);
		}
	}

	@Override
	public void fallbackErrorDescription(StringBuilder errors) {}
}
