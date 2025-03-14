package org.quiltmc.loader.impl.plugin.solvererror.graph;

import java.io.PrintWriter;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;

import org.quiltmc.loader.api.plugin.solver.LoadOption;

/**
 * Represents a link that a mod is mandatory and must be loaded.
 */
public class Mandatory implements RootLink {
	@NotNull
	final LoadOption option;

	public Mandatory(@NotNull LoadOption option) {
		this.option = option;
	}

	public Stream<LoadOption> children() {
		return Stream.of(option);
	}

	@Override
	public void dotGraphEdge(LoadOption from, PrintWriter dotGraph) {
	}

	@Override
	public String toString() {
		return "Manditory=" + option;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Mandatory mandatory = (Mandatory) o;

		return option.equals(mandatory.option);
	}

	@Override
	public int hashCode() {
		return option.hashCode();
	}
}
