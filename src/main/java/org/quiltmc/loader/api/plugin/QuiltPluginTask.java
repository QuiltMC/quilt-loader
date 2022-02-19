package org.quiltmc.loader.api.plugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.quiltmc.loader.impl.plugin.QuiltPluginTaskImpl;

/** Quilt plugin version of a {@link Future}.
 * <p>
 * All implementations use {@link CompletableFuture} internally, since we only need to restrict the API surface of this,
 * rather than have a complete implementation of concurrent code. */
public interface QuiltPluginTask<V> {

	/** @return True if this task has already finished - either successfully or exceptionally. */
	boolean isDone();

	/** @return The exception, if this task completed exceptionally, or null if it either hasn't completed or completed
	 *         successfully. */
	Throwable getException();

	/** @return The result, if the task completed successfully.
	 * @throws ExecutionException if the task completed unsuccessfully.
	 * @throws IllegalStateException if the task has not been completed yet. */
	V getResult() throws ExecutionException;

	/** @return A new {@link QuiltPluginTask} that has already been completed successfully, with the given result. */
	public static <V> QuiltPluginTask<V> createFinished(V result) {
		return QuiltPluginTaskImpl.createFinished(result);
	}

	/** @return A new {@link QuiltPluginTask} that has already been completed unsuccessfuly, with the given
	 *         exception. */
	public static <V> QuiltPluginTask<V> createFailed(Throwable cause) {
		return QuiltPluginTaskImpl.createFailed(cause);
	}
}
