package org.quiltmc.loader.impl.solver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.quiltmc.loader.util.sat4j.pb.tools.DependencyHelper;
import org.quiltmc.loader.util.sat4j.specs.ContradictionException;

/** Base definition of a link between one or more {@link LoadOption}s, that */
abstract class ModLink implements Comparable<ModLink> {

	/** Used for {@link #compareTo(ModLink)}. AFAIK this is only needed since sat4j uses a TreeMap rather than a
	 * HashMap for return links in {@link DependencyHelper#why()}. As we don't care about that order all that
	 * matters is we get a consistent comparison. */
	static final List<Class<? extends ModLink>> LINK_ORDER = new ArrayList<>();

	static {
		LINK_ORDER.add(MandatoryModIdDefinition.class);
		LINK_ORDER.add(OptionalModIdDefintion.class);
		LINK_ORDER.add(ModDep.class);
		LINK_ORDER.add(ModBreakage.class);
	}

	abstract ModLink put(DependencyHelper<LoadOption, ModLink> helper) throws ContradictionException;

	/** @return A description of the link. */
	@Override
	public abstract String toString();

	/**
	 * Checks to see if this link is [...]
	 * 
	 * @deprecated Not used yet. In the future this will be used for better error message generation.
	 */
	@Deprecated
	public boolean isNode() {
		return true;
	}

	/**
	 * TODO: Better name!
	 * 
	 * @deprecated Not used yet. In the future this will be used for better error message generation.
	 */
	@Deprecated
	public abstract Collection<? extends LoadOption> getNodesFrom();

	/**
	 * TODO: Better name!
	 * 
	 * @deprecated Not used yet. In the future this will be used for better error message generation.
	 */
	@Deprecated
	public abstract Collection<? extends LoadOption> getNodesTo();

	@Override
	public final int compareTo(ModLink o) {
		if (o.getClass() == getClass()) {
			return compareToSelf(o);
		} else {
			int i0 = LINK_ORDER.indexOf(getClass());
			int i1 = LINK_ORDER.indexOf(o.getClass());
			if (i0 < 0) {
				throw new IllegalStateException("Unknown " + getClass() + " (It's not registered in ModLink.LINK_ORDER!)");
			}
			if (i1 < 0) {
				throw new IllegalStateException("Unknown " + o.getClass() + " (It's not registered in ModLink.LINK_ORDER!)");
			}
			return Integer.compare(i1, i0);
		}
	}

	protected abstract int compareToSelf(ModLink o);
}
