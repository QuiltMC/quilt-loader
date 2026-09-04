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
	private final List<RowEntry> rows = new ArrayList<>();

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

		void computeWidth(List<RowEntry> rows) {
			if (maxWidth >= 0) {
				return;
			} else {
				maxWidth = Math.max(maxWidth, name.asciiWidth);
				for (RowEntry rowEntry : rows) {
					if (!(rowEntry instanceof AsciiTableRow)) {
						continue;
					}
					AsciiTableRow row = (AsciiTableRow) rowEntry;
					AsciiTableCell value = row.entries.get(this);
					if (value != null) {
						maxWidth = Math.max(maxWidth, value.asciiWidth);
					}
				}
			}
		}
	}

	private interface RowEntry {}

	private enum SeparatorRow implements RowEntry {
		BLANK,
		BAR;
	}

	public static final class AsciiTableRow implements RowEntry {
		private final Map<AsciiTableColumn, AsciiTableCell> entries = new HashMap<>();

		public void put(AsciiTableColumn column, String value) {
			AsciiTableCell newCell = new AsciiTableCell(value);
			AsciiTableCell oldCell = entries.put(column, newCell);
			column.includeCell(oldCell, newCell);
		}

		public void put(AsciiTableColumn column, Object value) {
			put(column, value.toString());
		}

		public void put(AsciiTableColumn column, Object beforeGap, Object afterGap) {
			AsciiTableCell newCell = new AsciiTableCell(beforeGap.toString(), afterGap.toString());
			AsciiTableCell oldCell = entries.put(column, newCell);
			column.includeCell(oldCell, newCell);
		}
	}

	public static final class AsciiTableCell {
		public static final AsciiTableCell BLANK = new AsciiTableCell("");

		private final String value;
		private final String beforeGap, afterGap;
		private final int asciiWidth;

		public AsciiTableCell(String value) {
			if (value == null) {
				this.value = "";
			} else {
				this.value = value;
			}
			this.beforeGap = null;
			this.afterGap = null;
			this.asciiWidth = computeAsciiWidth(value);
		}

		public AsciiTableCell(String beforeGap, String afterGap) {
			this.value = null;
			this.beforeGap = beforeGap;
			this.afterGap = afterGap;
			this.asciiWidth = computeAsciiWidth(beforeGap) + computeAsciiWidth(afterGap);
		}

		@Override
		public String toString() {
			return value == null ? (beforeGap + afterGap) : value;
		}

		public void append(StringBuilder sb, AsciiTableColumn column) {
			if (value == null) {
				sb.append(beforeGap);
			} else if (!column.rightAligned) {
				sb.append(value);
			}

			for (int i = asciiWidth; i < column.maxWidth; i++) {
				sb.append(' ');
			}

			if (value == null) {
				sb.append(afterGap);
			} else if (column.rightAligned) {
				sb.append(value);
			}
		}
	}

	public AsciiTableColumn addColumn(String name, boolean rightAligned) {
		AsciiTableColumn column = new AsciiTableColumn(name, rightAligned);
		columns.add(column);
		return column;
	}

	public AsciiTableColumn insertColumnBefore(AsciiTableColumn succeding, String name, boolean rightAligned) {
		AsciiTableColumn column = new AsciiTableColumn(name, rightAligned);
		columns.add(columns.indexOf(succeding), column);
		return column;
	}

	public AsciiTableColumn insertColumnAfter(AsciiTableColumn preceding, String name, boolean rightAligned) {
		AsciiTableColumn column = new AsciiTableColumn(name, rightAligned);
		columns.add(columns.indexOf(preceding) + 1, column);
		return column;
	}

	public AsciiTableRow addRow() {
		AsciiTableRow row = new AsciiTableRow();
		rows.add(row);
		return row;
	}

	public void addBlankRow() {
		rows.add(SeparatorRow.BLANK);
	}

	public void addBarRow() {
		rows.add(SeparatorRow.BAR);
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

		for (RowEntry rowEntry : rows) {

			if (rowEntry == SeparatorRow.BAR) {
				dst.accept(sep);
				continue;
			}

			final AsciiTableRow row;

			if (rowEntry instanceof AsciiTableRow) {
				row = (AsciiTableRow) rowEntry;
			} else {
				assert rowEntry == SeparatorRow.BLANK;
				row = null;
			}

			for (AsciiTableColumn column : columns) {
				AsciiTableCell cell = row != null ? row.entries.get(column) : null;
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
