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

package org.quiltmc.loader.impl.report;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Helper class for writing a 'report' file to the game directory (either a crash report, a simulated load failed
 * report, or an informational mod state report). */
public class QuiltReport {

	private final String header;
	private final List<String> overview = new ArrayList<>();
	private final List<QuiltReportSection> sections = new ArrayList<>();

	public QuiltReport(String header) {
		this.header = header;
	}

	public void overview(String... lines) {
		Collections.addAll(overview, lines);
	}

	public <S extends QuiltReportSection> S addSection(S section) {
		sections.add(section);
		return section;
	}

	public QuiltStringSection addStringSection(String name, int ordering, String... lines) {
		return addSection(new QuiltStringSection(name, ordering, lines));
	}

	public QuiltStacktraceSection addStacktraceSection(String name, int ordering, Throwable ex) {
		return addSection(new QuiltStacktraceSection(ordering, name, ex));
	}

	public List<QuiltReportSection> getSections() {
		sections.sort(null);
		return Collections.unmodifiableList(sections);
	}

	public void write(Path to) throws IOException {
		try (BufferedWriter writer = Files.newBufferedWriter(to, StandardOpenOption.CREATE_NEW)) {
			PrintWriter printer = new PrintWriter(writer);
			write(printer);
			printer.flush();
		}
	}

	public void write(PrintWriter to) {
		writeInternal(to, false);
	}

	private void writeInternal(PrintWriter to, boolean toLog) {
		to.println("---- " + header + " ----");

		LocalDateTime now = LocalDateTime.now();
		to.println("Date/Time: " + now.format(DateTimeFormatter.ofPattern("yyyy/MM/dd kk:mm:ss.SSSS")));

		for (String line : overview) {
			to.println(line);
		}

		to.println();

		sections.sort(null);

		for (QuiltReportSection section : sections) {

			if (toLog && !section.showInLogs) {
				continue;
			}

			to.println("-- " + section.name + " --");
			to.println();
			section.write(to);
			to.println();
			to.println();
		}

		to.println("---- end of report ----");
		to.flush();
	}

	public void writeToLog() {
		PrintWriter writer = new PrintWriter(System.out);
		try {
			writeInternal(writer, true);
		} finally {
			writer.flush();
		}
	}
}
