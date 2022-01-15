package org.quiltmc.loader.impl.memfilesys;

import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.Watchable;
import java.util.Collections;
import java.util.List;

final class QuiltWatchKey implements WatchKey {

	final Watchable watched;

	QuiltWatchKey(Watchable watched) {
		this.watched = watched;
	}

	@Override
	public boolean isValid() {
		return true;
	}

	@Override
	public List<WatchEvent<?>> pollEvents() {
		return Collections.emptyList();
	}

	@Override
	public boolean reset() {
		return true;
	}

	@Override
	public void cancel() {}

	@Override
	public Watchable watchable() {
		return watched;
	}
}
