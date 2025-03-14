package org.quiltmc.loader.impl.plugin.solvererror.graph;

import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;

import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleDepOnly;

/**
 * Represents a depend that has its requirement.
 */
public class Depends implements DepLink<QuiltRuleDepOnly> {
	@NotNull
	final QuiltRuleDepOnly rule;
	@NotNull
	final LoadOption depends;

	public Depends(@NotNull LoadOption depends, @NotNull QuiltRuleDepOnly rule) {
		this.depends = depends;
		this.rule = rule;
	}

	public Stream<LoadOption> children() {
		return Stream.of(depends);
	}

	@Override
	public @NotNull QuiltRuleDepOnly getRule() {
		return this.rule;
	}

	@Override
	public String toString() {
		return "Depends=" + depends;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Depends depends1 = (Depends) o;

		if (!rule.equals(depends1.rule)) return false;
		return depends.equals(depends1.depends);
	}

	@Override
	public int hashCode() {
		int result = rule.hashCode();
		result = 31 * result + depends.hashCode();
		return result;
	}
}
