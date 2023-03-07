package org.quiltmc.loader.impl.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.impl.FormattedException;
import org.quiltmc.loader.impl.gui.QuiltJsonGui.QuiltTreeWarningLevel;

public final class QuiltStatusNode extends QuiltGuiSyncBase {
	private final QuiltStatusNode parent;

	public String name;

	/** The icon type. There can be a maximum of 2 decorations (added with "+" symbols), or 3 if the
	 * {@link #setWarningLevel(QuiltTreeWarningLevel) warning level} is set to
	 * {@link QuiltTreeWarningLevel#NONE } */
	public String iconType = QuiltJsonGui.ICON_TYPE_DEFAULT;

	private QuiltTreeWarningLevel warningLevel = QuiltTreeWarningLevel.NONE;

	public boolean expandByDefault = false;

	public final List<QuiltStatusNode> children = new ArrayList<>();

	/** Extra text for more information. Lines should be separated by "\n". */
	public String details;

	QuiltStatusNode(QuiltStatusNode parent, String name) {
		super(parent);
		this.parent = parent;
		this.name = name;
	}

	QuiltStatusNode(QuiltGuiSyncBase parent, LoaderValue.LObject obj) throws IOException {
		super(parent, obj);
		if (parent instanceof QuiltStatusNode) {
			this.parent = (QuiltStatusNode) parent;
		} else {
			this.parent = null;
		}
		name = HELPER.expectString(obj, "name");
		iconType = HELPER.expectString(obj, "icon");
		warningLevel = QuiltTreeWarningLevel.read(HELPER.expectString(obj, "level"));
		expandByDefault = HELPER.expectBoolean(obj, "expandByDefault");
		details = obj.containsKey("details") ? HELPER.expectString(obj, "details") : null;
		for (LoaderValue sub : HELPER.expectArray(obj, "children")) {
			children.add(readChild(sub, QuiltStatusNode.class));
		}
	}

	@Override
	protected void write0(Map<String, LoaderValue> map) {
		map.put("name", lvf().string(name));
		map.put("icon", lvf().string(iconType));
		map.put("level", lvf().string(warningLevel.lowerCaseName));
		map.put("expandByDefault", lvf().bool(expandByDefault));
		if (details != null) {
			map.put("details", lvf().string(details));
		}
		map.put("children", lvf().array(write(children)));
	}

	@Override
	String syncType() {
		return "tree_node";
	}

	public QuiltTreeWarningLevel getMaximumWarningLevel() {
		return warningLevel;
	}

	public void setWarningLevel(QuiltTreeWarningLevel level) {
		if (this.warningLevel == level || level == null) {
			return;
		}

		if (warningLevel.isHigherThan(level)) {
			// Reject high -> low level changes, since it's probably a mistake
		} else {
			if (parent != null && level.isHigherThan(parent.warningLevel)) {
				parent.setWarningLevel(level);
			}

			this.warningLevel = level;
		}
	}

	public void setError() {
		setWarningLevel(QuiltTreeWarningLevel.ERROR);
	}

	public void setWarning() {
		setWarningLevel(QuiltTreeWarningLevel.WARN);
	}

	public void setInfo() {
		setWarningLevel(QuiltTreeWarningLevel.INFO);
	}

	public QuiltStatusNode addChild(String string) {
		int indent = 0;
		QuiltTreeWarningLevel level = null;

		while (string.startsWith("\t")) {
			indent++;
			string = string.substring(1);
		}

		string = string.trim();

		if (string.length() > 1) {
			if (Character.isWhitespace(string.charAt(1))) {
				level = QuiltTreeWarningLevel.fromChar(string.charAt(0));

				if (level != null) {
					string = string.substring(2);
				}
			}
		}

		string = string.trim();
		String icon = "";

		if (string.length() > 3) {
			if ('$' == string.charAt(0)) {
				Pattern p = Pattern.compile("\\$([a-z.+-]+)\\$");
				Matcher match = p.matcher(string);
				if (match.find()) {
					icon = match.group(1);
					string = string.substring(icon.length() + 2);
				}
			}
		}

		string = string.trim();

		QuiltStatusNode to = this;

		for (; indent > 0; indent--) {
			if (to.children.isEmpty()) {
				QuiltStatusNode node = new QuiltStatusNode(to, "");
				to.children.add(node);
				to = node;
			} else {
				to = to.children.get(to.children.size() - 1);
			}

			to.expandByDefault = true;
		}

		QuiltStatusNode child = new QuiltStatusNode(to, string);
		child.setWarningLevel(level);
		child.iconType = icon;
		to.children.add(child);
		return child;
	}

	public QuiltStatusNode addException(Throwable exception) {
		return addException(
			this, Collections.newSetFromMap(new IdentityHashMap<>()), exception, UnaryOperator.identity()
		);
	}

	public QuiltStatusNode addCleanedException(Throwable exception) {
		return addException(this, Collections.newSetFromMap(new IdentityHashMap<>()), exception, e -> {
			// Remove some self-repeating exception traces from the tree
			// (for example the RuntimeException that is is created unnecessarily by ForkJoinTask)
			Throwable cause;

			while ((cause = e.getCause()) != null) {
				if (e.getSuppressed().length > 0) {
					break;
				}

				String msg = e.getMessage();

				if (msg == null) {
					msg = e.getClass().getName();
				}

				if (!msg.equals(cause.getMessage()) && !msg.equals(cause.toString())) {
					break;
				}

				e = cause;
			}

			return e;
		});
	}

	private static QuiltStatusNode addException(QuiltStatusNode node, Set<Throwable> seen, Throwable exception,
		UnaryOperator<Throwable> filter) {
		if (!seen.add(exception)) {
			return node;
		}

		exception = filter.apply(exception);
		QuiltStatusNode sub = node.addExceptionNode(exception);

		for (Throwable t : exception.getSuppressed()) {
			addException(sub, seen, t, filter);
		}

		if (exception.getCause() != null) {
			addException(sub, seen, exception.getCause(), filter);
		}

		return sub;
	}

	private QuiltStatusNode addExceptionNode(Throwable exception) {
		String msg;

		if (exception instanceof FormattedException) {
			msg = Objects.toString(exception.getMessage());
		} else if (exception.getMessage() == null || exception.getMessage().isEmpty()) {
			msg = exception.toString();
		} else {
			msg = String.format("%s: %s", exception.getClass().getSimpleName(), exception.getMessage());
		}

		String[] lines = msg.split("\n");

		QuiltStatusNode sub = new QuiltStatusNode(this, lines[0]);
		children.add(sub);
		sub.setError();
		sub.expandByDefault = true;

		for (int i = 1; i < lines.length; i++) {
			sub.addChild(lines[i]);
		}

		return sub;
	}

	public QuiltStatusNode getFileNode(String file, String folderType, String fileType) {
		QuiltStatusNode fileNode = this;

		pathIteration: for (String s : file.split("/")) {
			if (s.isEmpty()) {
				continue;
			}

			for (QuiltStatusNode c : fileNode.children) {
				if (c.name.equals(s)) {
					fileNode = c;
					continue pathIteration;
				}
			}

			if (fileNode.iconType.equals(QuiltJsonGui.ICON_TYPE_DEFAULT)) {
				fileNode.iconType = folderType;
			}

			fileNode = fileNode.addChild(s);
		}

		fileNode.iconType = fileType;
		return fileNode;
	}
}
