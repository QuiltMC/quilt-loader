package org.quiltmc.loader.impl.report;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuiltStringSection extends QuiltReportSection {

	private final List<String> lines = new ArrayList<>();

	public QuiltStringSection(String name, int ordering, String... lines) {
		super(ordering, name);
		Collections.addAll(this.lines, lines);
	}

	public void lines(String... lines) {
		Collections.addAll(this.lines, lines);
	}

	@Override
	public void write(PrintWriter to) {
		for (String line : lines) {
			to.println(line);
		}
	}
}
