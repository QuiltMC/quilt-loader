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

	final List<MultiModLink> multiLinks;

	public ModLink() {
		multiLinks = new ArrayList<>();
	}

	/** Special constructor, solely for {@link MultiModLink}. */
	ModLink(ModLink real) {
		if (getClass() != MultiModLink.class) {
			throw new IllegalStateException("This constructor is only meant to be used by MultiModLink!");
		}
		this.multiLinks = real.multiLinks;
	}

	abstract ModLink put(DependencyHelper<LoadOption, ModLink> helper) throws ContradictionException;

	public final void remove(DependencyHelper<LoadOption,ModLink> helper) {
		helper.quilt_removeConstraint(this);
		for (MultiModLink link : multiLinks) {
			 helper.quilt_removeConstraint(link);
		}
		multiLinks.clear();
	}

	protected final MultiModLink createAdditional() {
		MultiModLink link = new MultiModLink(this);
		multiLinks.add(link);
		return link;
	}

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
				i0 = LINK_ORDER.size();
				LINK_ORDER.add(getClass());
			}
			if (i1 < 0) {
				i1 = LINK_ORDER.size();
				LINK_ORDER.add(o.getClass());
			}
			return Integer.compare(i1, i0);
		}
	}

	protected abstract int compareToSelf(ModLink o);

	public abstract void fallbackErrorDescription(StringBuilder errors);
}
