package org.quiltmc.loader.impl.report;

import org.quiltmc.loader.impl.discovery.ModResolutionException;

public class QuiltReportedError extends ModResolutionException {

	public final QuiltReport report;

	public QuiltReportedError(QuiltReport report) {
		this.report = report;
	}
}
