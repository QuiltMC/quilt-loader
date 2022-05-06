package org.quiltmc.loader.api.config;

import java.util.function.Consumer;

public interface MetadataContainerBuilder<SELF extends MetadataContainerBuilder<SELF>> {
	/**
	 * Create or configure a piece of metadata
	 *
	 * @param type the type of metadata to configure
	 * @param builderConsumer the modifications to be made to the piece of metadata
	 * @return this
	 */
	<M, B extends MetadataType.Builder<M>> SELF metadata(MetadataType<M, B> type, Consumer<B> builderConsumer);
}
