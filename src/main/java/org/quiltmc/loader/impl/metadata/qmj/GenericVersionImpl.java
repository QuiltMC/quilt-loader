/*
 * Copyright 2022 QuiltMC
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

package org.quiltmc.loader.impl.metadata.qmj;

import org.quiltmc.loader.api.Version;

public class GenericVersionImpl implements Version.Raw {
	private final String raw;

	public GenericVersionImpl(String raw) {
		this.raw = raw;
	}

	@Override
	public String raw() {
		return raw;
	}

	@Override
	public String toString() {
		return raw;
	}

	@Override
	public int hashCode() {
		return raw.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Version.Raw) {
			return raw.equals(((Version.Raw) obj).raw());
		} else {
			return false;
		}
	}

	@Override
	public int compareTo(Version other) {
		return compareRaw(raw(), other.raw());
	}
	// it's difficult to generically deal with comparables - these operations are safe
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static int compareRaw(String a, String b) {
		// Arrays.compare gives wrong precedence to array length, causing 1.0.1 to sort as older than 1.0.0_01
		// so, implement it ourselves from scratch
		List<Comparable<?>> ad = decompose(a);
		List<Comparable<?>> bd = decompose(b);
		for (int i = 0; i < Math.max(ad.size(), bd.size()); i++) {
			Comparable ac = i >= ad.size() ? null : ad.get(i);
			Comparable bc = i >= bd.size() ? null : bd.get(i);
			if (typeof(ac) != typeof(bc)) {
				// Comparables assume the input object is the same type as the object, so we need to
				// ensure that's true; this will happen in the case of two critically mismatched
				// versions that contain a symbol where a number is expected - in this case, lexical
				// is the best we can do
				ac = ac == null ? null : ac.toString();
				bc = bc == null ? null : bc.toString();
			}
			int c;
			if (bc == null && isSemverPrerelease(ac)) {
				// special case: compatibility with semver, which sorts "pre-releases" differently
				c = -1;
			} else if (ac == null && isSemverPrerelease(bc)) {
				c = 1;
			} else if (ac == null) {
				// special case: nulls are *always* lesser
				// bc cannot be null here, don't need to check
				c = -1;
			} else {
				c = bc == null ? 1 : ac.compareTo(bc);
			}
			if (c != 0) return c;
		}
		return 0;
	}
	
	/*
	 * Break apart a string into "logical" version components, by splitting it where a string
	 * of characters changes from numeric to non-numeric.
	 */
	private static List<Comparable<?>> decompose(String str) {
		if (str.isEmpty()) return List.of();
		boolean lastWasNumber = Character.isDigit(str.codePointAt(0));
		StringBuilder accum = new StringBuilder();
		List<Comparable<?>> out = new ArrayList<>();
		// remove appendices
		int plus = str.lastIndexOf('+');
		if (plus != -1) str = str.substring(0, plus);
		for (int cp : str.codePoints().toArray()) {
			boolean number = Character.isDigit(cp);
			if (number != lastWasNumber) {
				complete(lastWasNumber, accum, out);
				lastWasNumber = number;
			}
			accum.appendCodePoint(cp);
		}
		complete(lastWasNumber, accum, out);
		return out;
	}

	private static void complete(boolean number, StringBuilder accum, List<Comparable<?>> out) {
		String s = accum.toString();
		if (number) {
			// just in case someone uses a pointlessly long version string...
			out.add(Long.parseLong(s));
		} else {
			out.add(s);
		}
		accum.setLength(0);
	}
	
	private static boolean isSemverPrerelease(Object o) {
		if (o instanceof String) {
			String s = (String)o;
			return s.length() > 1 && s.charAt(0) == '-';
		}
		return false;
	}

	private static Class<?> typeof(Object o) {
		return o == null ? null : o.getClass();
	}
}
