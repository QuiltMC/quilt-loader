package org.quiltmc.loader.api.gui;

/** Base interface for all gui windows opened by loader.
 * <p>
 * Common properties:
 * <ul>
 * <li>A title, in {@link QuiltLoaderText} form.</li>
 * <li>An icon, in {@link QuiltLoaderIcon} form.</li>
 * </ul>
 * 
 * @param <R> The return type for this window. This can be obtained with {@link #returnValue()}. */
public interface QuiltLoaderWindow<R> {

	QuiltLoaderText title();

	void title(QuiltLoaderText title);

	QuiltLoaderIcon icon();

	void icon(QuiltLoaderIcon icon);

	R returnValue();

	void returnValue(R value);

	/** Adds a listener that will be invoked when this window is closed by the user. */
	void addClosedListener(Runnable onCloseListener);
}
