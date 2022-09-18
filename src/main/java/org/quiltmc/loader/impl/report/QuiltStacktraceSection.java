package org.quiltmc.loader.impl.report;

import java.io.PrintWriter;

public class QuiltStacktraceSection extends QuiltReportSection {

	private final Throwable throwable;

	public QuiltStacktraceSection(int ordering, String name, Throwable throwable) {
		super(ordering, name);
		this.throwable = throwable;
	}

	@Override
	public void write(PrintWriter to) {
		throwable.printStackTrace(to);
	}
}
