package org.quiltmc.loader.impl.solver;

import java.util.Map;

import org.quiltmc.loader.api.plugin.solver.LoadOption;

/** Returned by {@link RuleDefinition#computeConstants(Map)}. Valid values:
 * <ol>
 * <li>{@link #CONTRADICTION}</li>
 * <li>{@link #IDENTICAL}</li>
 * <li>{@link #TRIVIALLY_REMOVED}</li>
 * <li>{@link Changed}</li>
 * <li>{@link Removed}</li>
 * </ol>
 */
/* sealed */ interface RuleComputeResult {
	static final RuleComputeResult CONTRADICTION = SimpleResult.CONTRADICTION;
	static final RuleComputeResult IDENTICAL = SimpleResult.IDENTICAL;
	static final RuleComputeResult TRIVIALLY_REMOVED = SimpleResult.TRIVIALLY_REMOVED;

	enum SimpleResult implements RuleComputeResult {
		CONTRADICTION,
		IDENTICAL,
		TRIVIALLY_REMOVED;
	}

	static abstract /* sealed */ class DeclaredConstants implements RuleComputeResult {
		final Map<LoadOption, Boolean> newConstants;

		private DeclaredConstants(Map<LoadOption, Boolean> newConstants) {
			this.newConstants = newConstants;
		}
	}

	/** Indicates that the rule definition fully inlined to constants, and doesn't need to be present in solving any
	 * more. */
	static final class Removed extends DeclaredConstants {

		Removed(Map<LoadOption, Boolean> newConstants) {
			super(newConstants);
		}
	}

	/** Indicates that the rule definition partially inlined to constants, and a different rule should now be used
	 * instead. */
	static final class Changed extends DeclaredConstants {
		final RuleDefinition newDefintion;

		Changed(RuleDefinition newDefintion, Map<LoadOption, Boolean> newConstants) {
			super(newConstants);
			this.newDefintion = newDefintion;
		}
	}

}
