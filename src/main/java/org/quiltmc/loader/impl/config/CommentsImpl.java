package org.quiltmc.loader.impl.config;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.quiltmc.loader.api.config.Comments;
import org.quiltmc.loader.impl.util.ImmutableIterable;

public final class CommentsImpl implements Comments {
	private final List<String> comments;

	public CommentsImpl(List<String> comments) {
		this.comments = new ArrayList<>(comments);
	}

	@NotNull
	@Override
	public Iterator<String> iterator() {
		return new ImmutableIterable<>(this.comments).iterator();
	}
}
