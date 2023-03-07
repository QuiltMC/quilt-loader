package org.quiltmc.loader.api.gui;

/** Thrown by {@link QuiltLoaderGui#openErrorGui} if the user closed the error gui without actually fixing the error.
 * This generally means the game should crash. */
public final class LoaderGuiClosed extends Exception {
	public static final LoaderGuiClosed INSTANCE = new LoaderGuiClosed();

	private LoaderGuiClosed() {
		super(null, null, false, false);
	}
}
