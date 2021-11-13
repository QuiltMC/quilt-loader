package org.quiltmc.loader.api.plugin.solver;

/** {@link LoadOption}s can implement this if they must be processed before they can be used, if they are selected.
 * <p>
 * Unselected {@link TentativeLoadOption}s are left alone at the end of the cycle, and are not resolved.
 */
public interface TentativeLoadOption {

}
