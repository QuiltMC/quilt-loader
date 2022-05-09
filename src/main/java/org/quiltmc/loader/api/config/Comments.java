package org.quiltmc.loader.api.config;

/**
 * A list of strings that is used by {@link org.quiltmc.loader.api.config.annotations.Comment} as a container for any
 * number of comments that might be associated with a given {@link TrackedValue} or {@link Config}
 */
public interface Comments extends Iterable<String> {
}
