package org.quiltmc.loader.impl.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.quiltmc.loader.api.gui.QuiltDisplayedError;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.impl.plugin.solvererror.SolverError;
import org.quiltmc.loader.impl.plugin.solvererror.graph.Graph;

public class SolverErrorReportContext {
	private final QuiltPluginManagerImpl manager;
	private final List<SolverError> errors;
	private final Graph graph;

	public SolverErrorReportContext(QuiltPluginManagerImpl manager, List<SolverError> errors, Graph graph) {
		this.manager = manager;
		this.errors = errors;
		this.graph = graph;
	}

	public QuiltDisplayedError createError(QuiltLoaderText title) {
		return this.manager.theQuiltPluginContext.reportError(title);
	}

	public List<Integer> getRelatedErrors(LoadOption option, SolverError thisError) {
		List<Integer> related = new ArrayList<>();
		for (int i = 0; i < this.errors.size(); i++) {
			SolverError error = this.errors.get(i);
			if (thisError != error && error.isRelatedOption(option)) {
				related.add(i + 1);
			}
		}

		if (this.graph.isProvided(option)) {
			related.addAll(this.getRelatedErrors(this.graph.getProvidingMod(option), thisError));
			related = related.stream().sorted().distinct().collect(Collectors.toList());
		}

		return related;
	}

	public QuiltPluginManagerImpl getManager() {
		return manager;
	}

	public List<SolverError> getErrors() {
		return errors;
	}

	public Graph getGraph() {
		return graph;
	}
}
