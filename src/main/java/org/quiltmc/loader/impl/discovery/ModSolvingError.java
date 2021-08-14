package org.quiltmc.loader.impl.discovery;

/** Thrown when something goes wrong internally during solving, rather than being the fault of mod files. In other words
 * it's caused by a bug in quilt loader, or one of it's plugins. */
public class ModSolvingError extends ModResolutionException {
	public ModSolvingError(String s) {
		super(s);
	}

	public ModSolvingError(Throwable t) {
		super(t);
	}

	public ModSolvingError(String s, Throwable t) {
		super(s, t);
	}
}
