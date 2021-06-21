package org.quiltmc.loader.api;

/** @deprecated Since this is very much not a good API, and needs changing.
 *             <p>
 *             Or at least re-evaluted later on to see if this should be kept as-is. */
@Deprecated
public interface ConvertibleModMetadata {
    net.fabricmc.loader.api.metadata.ModMetadata asFabricModMetadata();

    org.quiltmc.loader.api.ModMetadata asQuiltModMetadata();
}
