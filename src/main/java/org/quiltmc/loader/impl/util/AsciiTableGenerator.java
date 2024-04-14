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

package org.quiltmc.loader.impl.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class AsciiTableGenerator {
	private final List<AsciiTableColumn> columns = new ArrayList<>();
	private final List<AsciiTableRow> rows = new ArrayList<>();

	public static final class AsciiTableColumn {
		AsciiTableCell name;
		boolean rightAligned = false;
		int maxWidth = 0;

		AsciiTableColumn(String name, boolean rightAligned) {
			setName(name);
			this.rightAligned = rightAligned;
		}

		public void setName(String name) {
			includeCell(this.name, this.name = new AsciiTableCell(name));
		}

		void includeCell(AsciiTableCell oldText, AsciiTableCell newText) {
			if (maxWidth == -1) {
				return;
			}
			int oldWidth = oldText != null ? oldText.asciiWidth : 0;
			int newWidth = newText.asciiWidth;
			if (newWidth > oldWidth) {
				maxWidth = Math.max(maxWidth, newWidth);
			} else if (newWidth < oldWidth) {
				if (oldWidth >= maxWidth) {
					maxWidth = -1;
				}
			}
		}

		void computeWidth(List<AsciiTableRow> rows) {
			if (maxWidth >= 0) {
				return;
			} else {
				maxWidth = Math.max(maxWidth, name.asciiWidth);
				for (AsciiTableRow row : rows) {
					AsciiTableCell value = row.entries.get(this);
					if (value != null) {
						maxWidth = Math.max(maxWidth, value.asciiWidth);
					}
				}
			}
		}
	}

	public static final class AsciiTableRow {
		private final Map<AsciiTableColumn, AsciiTableCell> entries = new HashMap<>();

		public void put(AsciiTableColumn column, String value) {
			AsciiTableCell newCell = new AsciiTableCell(value);
			AsciiTableCell oldCell = entries.put(column, newCell);
			column.includeCell(oldCell, newCell);
		}

		public void put(AsciiTableColumn column, Object value) {
			put(column, value.toString());
		}
	}

	public static final class AsciiTableCell {
		public static final AsciiTableCell BLANK = new AsciiTableCell("");

		private final String value;
		private final int asciiWidth;

		public AsciiTableCell(String value) {
			this.value = value;
			this.asciiWidth = computeAsciiWidth(value);
		}

		@Override
		public String toString() {
			return value;
		}

		public void append(StringBuilder sb, AsciiTableColumn column) {
			if (column.rightAligned) {
				for (int i = asciiWidth; i < column.maxWidth; i++) {
					sb.append(' ');
				}
				sb.append(value);
			} else {
				sb.append(value);
				for (int i = asciiWidth; i < column.maxWidth; i++) {
					sb.append(' ');
				}
			}
		}
	}

	public AsciiTableColumn addColumn(String name, boolean rightAligned) {
		AsciiTableColumn column = new AsciiTableColumn(name, rightAligned);
		columns.add(column);
		return column;
	}

	public AsciiTableRow addRow() {
		AsciiTableRow row = new AsciiTableRow();
		rows.add(row);
		return row;
	}

	public boolean isEmpty() {
		return rows.isEmpty();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		appendTable(line -> {
			sb.append(line);
			sb.append("\n");
		});
		return sb.toString();
	}

	public void appendTable(Consumer<String> dst) {
		for (AsciiTableColumn column : columns) {
			column.computeWidth(rows);
		}

		StringBuilder sbTab = new StringBuilder("|");
		StringBuilder sbSep = new StringBuilder("|");

		for (AsciiTableColumn column : columns) {
			sbTab.append(' ');
			sbSep.append('-');
			column.name.append(sbTab, column);
			for (int i = 0; i < column.maxWidth; i++) {
				sbSep.append('-');
			}
			sbSep.append(column.rightAligned ? ':' : '-');
			sbTab.append(" |");
			sbSep.append('|');
		}

		dst.accept(sbTab.toString());
		sbTab.setLength(0);
		sbTab.append("|");
		String sep = sbSep.toString();
		dst.accept(sep);

		for (AsciiTableRow row : rows) {
			for (AsciiTableColumn column : columns) {
				AsciiTableCell cell = row.entries.get(column);
				if (cell == null) {
					cell = AsciiTableCell.BLANK;
				}
				sbTab.append(' ');
				cell.append(sbTab, column);
				sbTab.append(" |");
			}
			dst.accept(sbTab.toString());
			sbTab.setLength(0);
			sbTab.append("|");
		}

		dst.accept(sep);
	}

	private static int computeAsciiWidth(String text) {
		return text == null ? 0 : text.length();
	}
}
