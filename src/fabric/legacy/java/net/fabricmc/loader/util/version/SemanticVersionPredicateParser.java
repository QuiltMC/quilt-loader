/*
 * Copyright 2023 QuiltMC
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

package net.fabricmc.loader.util.version;


import net.fabricmc.loader.api.metadata.version.VersionPredicate;

import java.util.function.Predicate;

/**
 * @deprecated Internal API, do not use
 */
@Deprecated
public final class SemanticVersionPredicateParser {
	public static Predicate<SemanticVersionImpl> create(String text) throws VersionParsingException {
		VersionPredicate predicate = VersionPredicate.parse(text);

		return predicate::test;
	}
}
