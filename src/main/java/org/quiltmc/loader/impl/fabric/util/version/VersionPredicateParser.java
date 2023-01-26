/*
 * Copyright 2022, 2023 QuiltMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.quiltmc.loader.impl.fabric.util.version;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.quiltmc.loader.api.VersionFormatException;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionComparisonOperator;
import net.fabricmc.loader.api.metadata.version.VersionInterval;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import net.fabricmc.loader.api.metadata.version.VersionPredicate.PredicateTerm;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public final class VersionPredicateParser {
	private static final VersionComparisonOperator[] OPERATORS = VersionComparisonOperator.values();

	public static VersionPredicate parse(String predicate) throws VersionParsingException {
		List<SingleVersionPredicate> predicateList = new ArrayList<>();

		for (String s : predicate.split(" ")) {
			s = s.trim();

			if (s.isEmpty() || s.equals("*")) {
				continue;
			}

			VersionComparisonOperator operator = VersionComparisonOperator.EQUAL;

			for (VersionComparisonOperator op : OPERATORS) {
				if (s.startsWith(op.getSerialized())) {
					operator = op;
					s = s.substring(op.getSerialized().length());
					break;
				}
			}

			org.quiltmc.loader.api.Version quiltVersion;
			try {
				quiltVersion = org.quiltmc.loader.impl.metadata.qmj.SemanticVersionImpl.ofFabricPermittingWildcard(s);
			} catch (VersionFormatException e) {
				quiltVersion = org.quiltmc.loader.api.Version.of(s);
			}
			Version version = Quilt2FabricVersion.toFabric(quiltVersion);

			if (version instanceof SemanticVersion) {
				SemanticVersion semVer = (SemanticVersion) version;

				if (semVer.hasWildcard()) { // .x version -> replace with conventional version by replacing the operator
					if (operator != VersionComparisonOperator.EQUAL) {
						throw new VersionParsingException("Invalid predicate: "+predicate+", version ranges with wildcards (.X) require using the equality operator or no operator at all!");
					}

					assert !semVer.getPrereleaseKey().isPresent();

					int compCount = semVer.getVersionComponentCount();
					assert compCount == 2 || compCount == 3;

					operator = compCount == 2 ? VersionComparisonOperator.SAME_TO_NEXT_MAJOR : VersionComparisonOperator.SAME_TO_NEXT_MINOR;

					int[] newComponents = new int[semVer.getVersionComponentCount() - 1];

					for (int i = 0; i < semVer.getVersionComponentCount() - 1; i++) {
						newComponents[i] = semVer.getVersionComponent(i);
					}

					try {
						version = Quilt2FabricVersion.toFabric(org.quiltmc.loader.api.Version.Semantic.of(newComponents, "", semVer.getBuildKey().orElse("")));
					} catch (VersionFormatException e) {
						throw new IllegalStateException("Failed to reconstruct a version from " + version, e);
					}
				}
			} else if (!operator.isMinInclusive() && !operator.isMaxInclusive()) { // non-semver without inclusive bound
				throw new VersionParsingException("Invalid predicate: "+predicate+", version ranges need to be semantic version compatible to use operators that exclude the bound!");
			} else { // non-semver with inclusive bound
				operator = VersionComparisonOperator.EQUAL;
			}

			predicateList.add(new SingleVersionPredicate(operator, version));
		}

		if (predicateList.isEmpty()) {
			return AnyVersionPredicate.INSTANCE;
		} else if (predicateList.size() == 1) {
			return predicateList.get(0);
		} else {
			return new MultiVersionPredicate(predicateList);
		}
	}

	public static Set<VersionPredicate> parse(Collection<String> predicates) throws VersionParsingException {
		Set<VersionPredicate> ret = new HashSet<>(predicates.size());

		for (String version : predicates) {
			ret.add(parse(version));
		}

		return ret;
	}

	public static VersionPredicate getAny() {
		return AnyVersionPredicate.INSTANCE;
	}

	static class AnyVersionPredicate implements VersionPredicate {
		static final VersionPredicate INSTANCE = new AnyVersionPredicate();

		private AnyVersionPredicate() { }

		@Override
		public boolean test(Version t) {
			return true;
		}

		@Override
		public List<? extends PredicateTerm> getTerms() {
			return Collections.emptyList();
		}

		@Override
		public VersionInterval getInterval() {
			return Quilt2FabricVersionInterval.INFINITE;
		}

		@Override
		public String toString() {
			return "*";
		}
	}

	static class SingleVersionPredicate implements VersionPredicate, PredicateTerm {
		private final VersionComparisonOperator operator;
		private final Version refVersion;
		private final VersionInterval interval;
		SingleVersionPredicate(VersionComparisonOperator operator, Version refVersion) {
			this.operator = operator;
			this.refVersion = refVersion;

			if (refVersion instanceof SemanticVersion) {
				SemanticVersion version = (SemanticVersion) refVersion;

				this.interval = new Quilt2FabricVersionInterval(operator.minVersion(version), operator.isMinInclusive(),
						operator.maxVersion(version), operator.isMaxInclusive());
			} else {
				this.interval = new Quilt2FabricVersionInterval(refVersion, true, refVersion, true);
			}
		}

		@Override
		public boolean test(Version version) {
			Objects.requireNonNull(version, "null version");

			return operator.test(version, refVersion);
		}

		@Override
		public List<PredicateTerm> getTerms() {
			return Collections.singletonList(this);
		}

		@Override
		public VersionInterval getInterval() {
			return interval;
		}

		@Override
		public VersionComparisonOperator getOperator() {
			return operator;
		}

		@Override
		public Version getReferenceVersion() {
			return refVersion;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof SingleVersionPredicate) {
				SingleVersionPredicate o = (SingleVersionPredicate) obj;

				return operator == o.operator && refVersion.equals(o.refVersion);
			} else {
				return false;
			}
		}

		@Override
		public int hashCode() {
			return operator.ordinal() * 31 + refVersion.hashCode();
		}

		@Override
		public String toString() {
			return operator.getSerialized().concat(refVersion.toString());
		}
	}

	static class MultiVersionPredicate implements VersionPredicate {
		private final List<SingleVersionPredicate> predicates;
		private final VersionInterval interval;
		MultiVersionPredicate(List<SingleVersionPredicate> predicates) {
			this.predicates = predicates;

			if (predicates.isEmpty()) {
				this.interval = AnyVersionPredicate.INSTANCE.getInterval();
			} else {
				VersionInterval ret = predicates.get(0).getInterval();

				for (int i = 1; i < predicates.size(); i++) {
					ret = Quilt2FabricVersionInterval.and(ret, predicates.get(i).getInterval());
				}

				this.interval = ret;
			}
		}

		@Override
		public boolean test(Version version) {
			Objects.requireNonNull(version, "null version");

			for (SingleVersionPredicate predicate : predicates) {
				if (!predicate.test(version)) return false;
			}

			return true;
		}

		@Override
		public List<? extends PredicateTerm> getTerms() {
			return predicates;
		}

		@Override
		public VersionInterval getInterval() {
			return interval;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof MultiVersionPredicate) {
				MultiVersionPredicate o = (MultiVersionPredicate) obj;

				return predicates.equals(o.predicates);
			} else {
				return false;
			}
		}

		@Override
		public int hashCode() {
			return predicates.hashCode();
		}

		@Override
		public String toString() {
			StringBuilder ret = new StringBuilder();

			for (SingleVersionPredicate predicate : predicates) {
				if (ret.length() > 0) ret.append(' ');
				ret.append(predicate.toString());
			}

			return ret.toString();
		}
	}
}