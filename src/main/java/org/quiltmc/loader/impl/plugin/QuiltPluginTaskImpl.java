package org.quiltmc.loader.impl.plugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.quiltmc.loader.api.plugin.QuiltPluginTask;

public final class QuiltPluginTaskImpl<V> implements QuiltPluginTask<V> {

	final CompletableFuture<V> future;

	QuiltPluginTaskImpl() {
		this.future = new CompletableFuture<>();
	}

	/** @return A new {@link QuiltPluginTask} that has already been completed successfully, with the given result. */
	public static <V> QuiltPluginTask<V> createFinished(V result) {
		QuiltPluginTaskImpl<V> task = new QuiltPluginTaskImpl<>();
		task.future.complete(result);
		return task;
	}

	/** @return A new {@link QuiltPluginTask} that has already been completed unsuccessfully, with the given
	 *         exception. */
	public static <V> QuiltPluginTask<V> createFailed(Throwable cause) {
		QuiltPluginTaskImpl<V> task = new QuiltPluginTaskImpl<>();
		task.future.completeExceptionally(cause);
		return task;
	}

	@Override
	public boolean isDone() {
		return future.isDone();
	}

	@Override
	public Throwable getException() {
		if (!future.isDone()) {
			return null;
		}

		try {
			future.get(0, TimeUnit.NANOSECONDS);
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new ExecutionException(e);
		} catch (ExecutionException e) {
			return e;
		} catch (TimeoutException e) {
			throw new IllegalStateException(
				"Apparently the CompletableFuture was done, but it threw a TimeoutException?", e
			);
		}
	}

	@Override
	public V getResult() throws ExecutionException {
		if (!future.isDone()) {
			throw new IllegalStateException("Task not complete yet!");
		}

		try {
			return future.get(0, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ExecutionException(e);
		} catch (ExecutionException e) {
			throw e;
		} catch (TimeoutException e) {
			throw new IllegalStateException(
				"Apparently the CompletableFuture was done, but it threw a TimeoutException?", e
			);
		}
	}
}
