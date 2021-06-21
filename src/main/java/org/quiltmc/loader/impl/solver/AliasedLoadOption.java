package org.quiltmc.loader.impl.solver;

import org.jetbrains.annotations.Nullable;

/** Indicates that this {@link LoadOption} should use {@link #getTarget()} for mod solving instead of this. */
public interface AliasedLoadOption {

    /** @return The {@link LoadOption} to use instead of this, or null if this can be used (and so this is not actually
     *         an alias). */
    @Nullable
    LoadOption getTarget();
}
