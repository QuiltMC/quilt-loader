package org.quiltmc.loader.impl.discovery;

/** Thrown when an exception occurs while solving the set of mods, which is caused by those mods - in other words the
 * user is expected to be able to fix this error by adding or removing mods, or by asking a mod author to fix their
 * quilt.mod.json file. */
public class ModSolvingException extends ModResolutionException {

	public ModSolvingException(String s) {
		super(s);
	}

	public ModSolvingException(Throwable t) {
		super(t);
	}

	public ModSolvingException(String s, Throwable t) {
		super(s, t);
	}
}
