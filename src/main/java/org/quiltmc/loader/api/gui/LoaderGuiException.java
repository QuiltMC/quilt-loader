package org.quiltmc.loader.api.gui;

/** Thrown by {@link QuiltLoaderGui#openErrorGui} if the error couldn't be opened. */
public class LoaderGuiException extends Exception {

	public LoaderGuiException(Throwable cause) {
		super(cause);
	}

	public LoaderGuiException(String message, Throwable cause) {
		super(message, cause);
	}

}
