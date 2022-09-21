package org.quiltmc.loader.impl.report;

import java.io.PrintWriter;

public abstract class QuiltReportSection implements Comparable<QuiltReportSection> {

	private final int ordering;
	final String name;
	boolean showInLogs = true;

	public QuiltReportSection(int ordering, String name) {
		this.ordering = ordering;
		this.name = name;
	}

	@Override
	public final int compareTo(QuiltReportSection o) {
		return Integer.compare(ordering, o.ordering);
	}

	public abstract void write(PrintWriter to);

	public void setShowInLogs(boolean b) {
		showInLogs = b;
	}
}
