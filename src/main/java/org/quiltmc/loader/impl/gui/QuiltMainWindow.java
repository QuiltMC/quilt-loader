/*
 * Copyright 2022, 2023 QuiltMC
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

package org.quiltmc.loader.impl.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.Desktop.Action;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Map.Entry;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.WindowConstants;
import javax.swing.border.Border;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

import org.quiltmc.loader.impl.gui.QuiltJsonGui.QuiltTreeWarningLevel;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.StringUtil;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
class QuiltMainWindow {
	static Icon missingIcon = null;

	static void open(QuiltJsonGui tree, boolean shouldWait) throws Exception {
		QuiltUI.init();

		SwingUtilities.invokeAndWait(() -> {
			new QuiltMainWindow(tree).open();
		});

		if (shouldWait) {
			tree.onClosedFuture.get();
		}
	}

	final JFrame window;
	final QuiltJsonGui jsonGui;
	final IconSet icons;

	public QuiltMainWindow(QuiltJsonGui tree) {
		this.jsonGui = tree;
		window = new JFrame();
		window.setVisible(false);
		window.setTitle(tree.title);

		try {
			List<BufferedImage> images = new ArrayList<>();
			images.add(loadImage("/ui/icon/quilt_x16.png"));
			images.add(loadImage("/ui/icon/quilt_x128.png"));
			window.setIconImages(images);
			setTaskBarImage(images.get(1));
		} catch (IOException e) {
			e.printStackTrace();
		}

		// TODO: change this back to normal after debugging
		window.setMinimumSize(new Dimension(1, 1));
		window.setPreferredSize(new Dimension(800, 480));
		window.setLocationByPlatform(true);
		window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		window.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				tree.onClosedFuture.complete(null);
			}
		});

		Container contentPane = window.getContentPane();

		if (tree.mainText != null && !tree.mainText.isEmpty()) {
			JLabel errorLabel = new JLabel(tree.mainText);
			errorLabel.setHorizontalAlignment(SwingConstants.CENTER);
			Font font = errorLabel.getFont();
			errorLabel.setFont(font.deriveFont(font.getSize() * 2.0f));
			contentPane.add(errorLabel, BorderLayout.NORTH);
		}

		icons = new IconSet(tree);

		if (tree.tabs.isEmpty() && tree.messages.isEmpty()) {
			QuiltJsonGuiTreeTab tab = new QuiltJsonGuiTreeTab(null, "Opening Errors");
			tab.addChild("No tabs provided! (Something is very broken)").setError();
			contentPane.add(createTreePanel(tab.node, tab.filterLevel, icons), BorderLayout.CENTER);
		} else if (tree.tabs.size() == 1 && tree.messages.isEmpty()) {
			QuiltJsonGuiTreeTab tab = tree.tabs.get(0);
			contentPane.add(createTreePanel(tab.node, tab.filterLevel, icons), BorderLayout.CENTER);
		} else if (tree.tabs.isEmpty()) {
			contentPane.add(createMessagesPanel(icons, tree.messages), BorderLayout.CENTER);
		} else {
			JTabbedPane tabs = new JTabbedPane();
			contentPane.add(tabs, BorderLayout.CENTER);

			if (!tree.messages.isEmpty()) {
				tabs.addTab(tree.messagesTabName, icons.get(new IconInfo("level_error")), createMessagesPanel(icons, tree.messages));
			}

			for (QuiltJsonGuiTreeTab tab : tree.tabs) {
				JPanel panel = createTreePanel(tab.node, tab.filterLevel, icons);
				QuiltTreeWarningLevel maxLevel = tab.node.getMaximumWarningLevel();
				if (maxLevel != QuiltTreeWarningLevel.NONE) {
					tabs.addTab(tab.node.name, icons.get(new IconInfo("level_" + maxLevel.lowerCaseName)), panel);
				} else {
					tabs.addTab(tab.node.name, panel);
				}
			}

		}

		if (!tree.buttons.isEmpty()) {
			JPanel buttons = new JPanel();
			contentPane.add(buttons, BorderLayout.SOUTH);
			buttons.setLayout(new FlowLayout(FlowLayout.TRAILING));

			for (QuiltJsonButton button : tree.buttons) {
				convertToJButton(buttons, button);
			}
		}
	}

	private void open() {
		window.pack();
		window.setVisible(true);
		window.requestFocus();
	}

	private void convertToJButton(JPanel addTo, QuiltJsonButton button) {
		JButton btn = button.icon.isEmpty() 
			? new JButton(button.text) 
			: new JButton(button.text, icons.get(IconInfo.parse(button.icon)));
		button.guiListener = new QuiltJsonButton.QuiltButtonListener() {
			@Override
			public void onTextChanged() {
				btn.setText(button.text);
			}

			@Override
			public void onIconChanged() {
				btn.setIcon(icons.get(IconInfo.parse(button.icon)));
			}

			@Override
			public void onEnabledChanged() {
				btn.setEnabled(button.enabled);
				btn.setToolTipText(button.disabledText);
			}
		};

		addTo.add(btn);
		btn.addActionListener(event -> {

			switch (button.action) {
				case CONTINUE: {
					jsonGui.onClosedFuture.complete(null);
					window.dispose();
					return;
				}
				case CLOSE: {
					window.dispose();
					return;
				}
				case VIEW_FILE: {
					browseFile(button.arguments.get("file"));
					return;
				}
				case VIEW_FOLDER: {
					browseFolder(button.arguments.get("folder"));
					return;
				}
				case OPEN_FILE: {
					openFile(button.arguments.get("file"));
					return;
				}
				case EDIT_FILE: {
					editFile(button.arguments.get("file"));
					return;
				}
				case OPEN_WEB_URL: {
					openWebUrl(button.arguments.get("url"));
					return;
				}
				case PASTE_CLIPBOARD_TEXT: {
					copyClipboardText(button.arguments.get("text"));
					return;
				}
				case PASTE_CLIPBOARD_FILE: {
					copyClipboardFile(button.arguments.get("file"));
					return;
				}
				case RETURN_SIGNAL_ONCE:
					button.enabled = false;
				case RETURN_SIGNAL_MANY: {
					button.sendClickToClient();
					break;
				}
				default:
					throw new IllegalStateException("Unknown / unimplemented action " + button.action);
			}

//			if (button.type == QuiltJsonGui.QuiltBasicButtonAction.CLICK_ONCE) btn.setEnabled(false);
		});
	}

	private void browseFile(String file) {
		// Desktop.browseFileDirectory exists!
		// But it's Java 9 only
		// However that doesn't stop us from trying it, since it works on mac
		// (and who knows, maybe it will get implemented at some point in the future?)
		if (browseFileJava9(file)) {
			return;
		}

		// And now for the ugly route
		if (browseFileNativeExec(file)) {
			return;
		}

		// If even that failed then we'll just admit defeat and open the file browser to the parent folder instead
		try {
			Desktop.getDesktop().open(new File(file).getParentFile());
		} catch (IOException | UnsupportedOperationException e) {
			JOptionPane.showMessageDialog(window, "Failed to open '" + file + "'");
			e.printStackTrace();
		}
	}

	private boolean browseFileJava9(String file) {
		Desktop d = Desktop.getDesktop();
		Desktop.Action action = null;
		try {
			action = Desktop.Action.valueOf("BROWSE_FILE_DIR");
		} catch (IllegalArgumentException invalidEnum) {
			action = null;
		}

		if (action != null && d.isSupported(action)) {
			try {
				Method method = d.getClass().getMethod("browseFileDirectory", File.class);
				method.invoke(d, new File(file));
				return true;
			} catch (ReflectiveOperationException e) {
				JOptionPane.showMessageDialog(window, "Failed to open '" + file + "'");
				e.printStackTrace();
			}
		}
		return false;
	}

	private boolean browseFileNativeExec(String file) {
		String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);

		if (osName.contains("windows")) {
			// This is fairly simple - there's only one file explorer
			// (a least, I assume most people just use the microsoft file explorer)
			try {
				Runtime.getRuntime().exec("explorer /select,\"" + file.replace("/", "\\") + "\"");
				return true;
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		} else if (osName.contains("mac")) {
			// Again, mac os just lets this work
			try {
				Runtime.getRuntime().exec("open -R \"" + file + "\"");
				return true;
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		} else if (osName.contains("linux")) {
			// Linux... is more complicated
			// Searching the internet shows us that 'xdg-open' is the *correct* thing to run
			// however that just opens the file in the appropriate "file viewer", not in a file explorer
			// Since this might be used for stuff like jar files that might either try to execute them
			// or open them in a zip viewer, which is not what we want.

			// So instead try a list of known file explorers
			// (Yes, this is a short list, and needs to be expanded)
			for (String[] cmd : new String[][] { { "nemo", "%s" }, { "nautilus", "%s" } }) {
				try {
					String[] result = new String[cmd.length];
					for (int i = 0; i < cmd.length; i++) {
						result[i] = cmd[i].replace("%s", file);
					}
					Runtime.getRuntime().exec(result);
					return true;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		return false;
	}

	private void browseFolder(String file) {
		try {
			Desktop.getDesktop().open(new File(file));
		} catch (IOException | UnsupportedOperationException e) {
			JOptionPane.showMessageDialog(window, "Failed to open '" + file + "'");
			e.printStackTrace();
		}
	}

	private void openFile(String file) {
		try {
			Desktop.getDesktop().open(new File(file));
		} catch (IOException | UnsupportedOperationException e) {
			JOptionPane.showMessageDialog(window, "Failed to open '" + file + "'");
			e.printStackTrace();
		}
	}

	private void editFile(String file) {
		try {
			Desktop desktop = Desktop.getDesktop();
			if (desktop.isSupported(Action.EDIT)) {
				desktop.edit(new File(file));
			} else {
				desktop.open(new File(file));
			}
		} catch (IOException | UnsupportedOperationException e) {
			JOptionPane.showMessageDialog(window, "Failed to edit '" + file + "'");
			e.printStackTrace();
		}
	}

	private void openWebUrl(String url) {
		try {
			URI uri = new URI(url);
			Desktop.getDesktop().browse(uri);
		} catch (URISyntaxException | IOException | UnsupportedOperationException e) {
			JOptionPane.showMessageDialog(window, "Failed to open '" + url + "'");
			e.printStackTrace();
		}
	}

	private void copyClipboardText(String text) {
		try {
			StringSelection clipboard = new StringSelection(text);
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(clipboard, clipboard);
		} catch (IllegalStateException e) {
			JOptionPane.showMessageDialog(window, "Failed to paste clipboard text!");
			e.printStackTrace();
		}
	}

	private void copyClipboardFile(String file) {
		try {
			String text = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);
			StringSelection clipboard = new StringSelection(text);
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(clipboard, clipboard);
		} catch (IllegalStateException e) {
			JOptionPane.showMessageDialog(window, "Failed to paste clipboard text!");
			e.printStackTrace();
		} catch (IOException e) {
			JOptionPane.showMessageDialog(window, "Failed to open file to paste from: '" + file + "'!");
			e.printStackTrace();
		}
	}

	private JComponent createMessagesPanel(IconSet icons, List<QuiltJsonGuiMessage> messages) {
		JScrollPane pane = new JScrollPane();
		pane.setOpaque(true);
		pane.getVerticalScrollBar().setUnitIncrement(16);

		JPanel outerPanel = new JPanel();
		outerPanel.setLayout(new BorderLayout());
		outerPanel.setBackground(Color.WHITE);

		JPanel panel = null;

		for (QuiltJsonGuiMessage message : messages) {
			if (panel == null) {
				panel = outerPanel;
			} else {
				JPanel outer = panel;
				panel = new JPanel();
				panel.setLayout(new BorderLayout());
				panel.setBackground(Color.WHITE);
				outer.add(panel, BorderLayout.CENTER);
			}
			panel.add(createMessagePanel(icons, message), BorderLayout.NORTH);
		}

		pane.setViewportView(outerPanel);
		return pane;
	}

	private JPanel createMessagePanel(IconSet icons, QuiltJsonGuiMessage message) {
		JPanel container = new JPanel();
		container.setLayout(new BorderLayout());
		container.setAlignmentY(0);

		JPanel top = new JPanel();
		top.setLayout(new BorderLayout());
		container.add(top, BorderLayout.NORTH);
		top.setAlignmentY(0);
		top.setAlignmentX(0);

		JLabel icon = new JLabel(icons.get(IconInfo.parse(message.iconType), 32));
		icon.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		top.add(icon, BorderLayout.WEST);
		JLabel title = new JLabel(message.title);
		title.setFont(title.getFont().deriveFont(Font.BOLD));
		top.add(title, BorderLayout.CENTER);

		JPanel panel = container;

		for (String desc : message.description) {
			JPanel outer = panel;
			panel = new JPanel();
			panel.setAlignmentY(0);
			panel.setLayout(new BorderLayout());
			outer.add(panel, BorderLayout.CENTER);

			panel.add(new JLabel(applyWrapping(desc)), BorderLayout.NORTH);
		}

		for (String info : message.additionalInfo) {
			JPanel outer = panel;
			panel = new JPanel();
			panel.setAlignmentY(0);
			panel.setLayout(new BorderLayout());
			outer.add(panel, BorderLayout.CENTER);

			JLabel label = new JLabel(applyWrapping(info));
			label.setFont(label.getFont().deriveFont(Font.ITALIC));
			panel.add(label, BorderLayout.NORTH);
		}

		message.listeners.add(new QuiltJsonGuiMessage.QuiltMessageListener() {
			@Override
			public void onFixed() {
				// temp
				icon.setIcon(icons.get(IconInfo.parse("tick"), 32));
			}
		});

		if (!message.buttons.isEmpty()) {
			JPanel buttons = new JPanel();
			buttons.setAlignmentY(0);
			buttons.setLayout(new FlowLayout(FlowLayout.LEADING));
			panel.add(buttons, BorderLayout.CENTER);

			for (QuiltJsonButton button : message.buttons) {
				convertToJButton(buttons, button);
			}
		}

		JPanel outer = new JPanel();
		outer.setAlignmentY(0);
		Border b0 = BorderFactory.createMatteBorder(5, 5, 0, 5, Color.WHITE);
		Border b1 = BorderFactory.createEtchedBorder();
		Border b2 = BorderFactory.createEmptyBorder(4, 4, 4, 4);
		outer.setBorder(BorderFactory.createCompoundBorder(b0, BorderFactory.createCompoundBorder(b1, b2)));
		outer.setLayout(new BorderLayout());
		outer.add(container, BorderLayout.WEST);

		return outer;
	}

	private static JPanel createTreePanel(QuiltStatusNode rootNode, QuiltTreeWarningLevel minimumWarningLevel,
										  IconSet iconSet) {

		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		TreeNode treeNode = new CustomTreeNode(null, rootNode, minimumWarningLevel);

		DefaultTreeModel model = new DefaultTreeModel(treeNode);
		JTree tree = new JTree(model);
		tree.setRootVisible(false);
		tree.setShowsRootHandles(true);
		tree.setRowHeight(0); // Allow rows to be multiple lines tall

		for (int row = 0; row < tree.getRowCount(); row++) {
			if (!tree.isVisible(tree.getPathForRow(row))) {
				continue;
			}

			CustomTreeNode node = ((CustomTreeNode) tree.getPathForRow(row).getLastPathComponent());

			if (node.node.expandByDefault) {
				tree.expandRow(row);
			}
		}

		ToolTipManager.sharedInstance().registerComponent(tree);
		tree.setCellRenderer(new CustomTreeCellRenderer(iconSet));

		JScrollPane scrollPane = new JScrollPane(tree);
		panel.add(scrollPane);

		return panel;
	}

	static BufferedImage loadImage(String str) throws IOException {
		return ImageIO.read(loadStream(str));
	}

	private static InputStream loadStream(String str) throws FileNotFoundException {
		InputStream stream = QuiltMainWindow.class.getResourceAsStream(str);

		if (stream == null) {
			throw new FileNotFoundException(str);
		}

		return stream;
	}

	private static void setTaskBarImage(Image image) {
		try {
			// TODO Remove reflection when updating past Java 8
			Class<?> taskbarClass = Class.forName("java.awt.Taskbar");
			Method getTaskbar = taskbarClass.getDeclaredMethod("getTaskbar");
			Method setIconImage = taskbarClass.getDeclaredMethod("setIconImage", Image.class);
			Object taskbar = getTaskbar.invoke(null);
			setIconImage.invoke(taskbar, image);
		} catch (Exception e) {
			// Ignored
		}
	}

	static final class IconSet {

		private final QuiltJsonGui tree;

		/** Map of IconInfo -> Integer Size -> Real Icon. */
		private final Map<IconInfo, Map<Integer, Icon>> icons = new HashMap<>();

		public IconSet(QuiltJsonGui tree) {
			this.tree = tree;
		}

		public Icon get(IconInfo info) {
			return get(info, 16);
		}

		public Icon get(IconInfo info, int scale) {
			// TODO: HDPI

			Map<Integer, Icon> map = icons.computeIfAbsent(info, k -> new HashMap<>());

			Icon icon = map.get(scale);

			if (icon == null) {
				try {
					icon = loadIcon(info, scale);
				} catch (IOException e) {
					e.printStackTrace();
					icon = missingIcon();
				}

				map.put(scale, icon);
			}

			return icon;
		}

		Icon loadIcon(IconInfo info, int scale) throws IOException {
			return new ImageIcon(generateIcon(info, scale));
		}

		BufferedImage generateIcon(IconInfo info, int scale) throws IOException {
			BufferedImage img = new BufferedImage(scale, scale, BufferedImage.TYPE_INT_ARGB);
			Graphics2D imgG2d = img.createGraphics();

			BufferedImage main = loadImage(info.mainPath, false, scale);
			if (main != null) {
				imgG2d.drawImage(main, 0, 0, scale, scale, null);
			}

			final int[][] coords = { { 0, scale / 2 }, { scale / 2, scale / 2 }, { scale / 2, 0 } };

			for (int i = 0; i < info.decor.length; i++) {
				String decor = info.decor[i];

				if (decor == null) {
					continue;
				}

				BufferedImage decorImg = loadImage(decor, true, scale);
				if (decorImg != null) {
					imgG2d.drawImage(decorImg, coords[i][0], coords[i][1], scale / 2, scale / 2, null);
				}
			}
			return img;
		}

		BufferedImage loadImage(String path, boolean isDecor, int scale) throws IOException {
			if (path.startsWith("!")) {
				int iconId = Integer.parseInt(path.substring(1));
				NavigableMap<Integer, BufferedImage> iconMap = QuiltForkServerMain.getCustomIcon(iconId);
				if (iconMap.isEmpty()) {
					return null;
				}
				Entry<Integer, BufferedImage> bestSource = iconMap.ceilingEntry(scale);
				if (bestSource == null) {
					bestSource = iconMap.floorEntry(scale);
				}
				return bestSource.getValue();
			}

			// Mandate correct scale
			// since we only ship x16 (main) and x8 (decor) we restrict file scale to that scale
			final int fileScale;
			if (isDecor) {
				fileScale = 8;
			} else {
				fileScale = 16;
			}
			return QuiltMainWindow.loadImage("/ui/icon/" + (isDecor ? "decoration/" : "") + path + "_x" + fileScale + ".png");
		}
	}

	private static Icon missingIcon() {
		if (missingIcon == null) {
			BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);

			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					img.setRGB(x, y, 0xff_ff_f2);
				}
			}

			for (int i = 0; i < 16; i++) {
				img.setRGB(0, i, 0x22_22_22);
				img.setRGB(15, i, 0x22_22_22);
				img.setRGB(i, 0, 0x22_22_22);
				img.setRGB(i, 15, 0x22_22_22);
			}

			for (int i = 3; i < 13; i++) {
				img.setRGB(i, i, 0x9b_00_00);
				img.setRGB(i, 16 - i, 0x9b_00_00);
			}

			missingIcon = new ImageIcon(img);
		}

		return missingIcon;
	}

	static final class IconInfo {
		public final String mainPath;
		public final String[] decor;
		private final int hash;

		IconInfo(String mainPath) {
			this.mainPath = mainPath;
			this.decor = new String[0];
			hash = mainPath.hashCode();
		}

		IconInfo(String mainPath, String[] decor) {
			this.mainPath = mainPath;
			this.decor = decor;
			assert decor.length < 4 : "Cannot fit more than 3 decorations into an image (and leave space for the background)";

			if (decor.length == 0) {
				// To mirror the no-decor constructor
				hash = mainPath.hashCode();
			} else {
				hash = mainPath.hashCode() * 31 + Arrays.hashCode(decor);
			}
		}

		public static IconInfo parse(String desc) {
			String[] split = desc.split("\\+");
			if (split.length == 0 || (split.length == 1 && split[0].isEmpty())) {
				return new IconInfo("missing");
			}

			List<String> decors = new ArrayList<>();
			// The warning gap
			decors.add(null);
			for (int i = 1; i < split.length && i < 3; i++) {
				decors.add(split[i]);
			}

			return new IconInfo(split[0], decors.toArray(new String[0]));
		}

		public static IconInfo fromNode(QuiltStatusNode node) {
			String[] split = node.iconType.split("\\+");
			System.out.println(Arrays.toString(split));

			if (split.length == 1 && split[0].isEmpty()) {
				split = new String[0];
			}

			final String main;
			List<String> decors = new ArrayList<>();
			QuiltTreeWarningLevel warnLevel = node.getMaximumWarningLevel();

			if (split.length == 0) {
				// Empty string, but we might replace it with a warning
				if (warnLevel == QuiltTreeWarningLevel.NONE) {
					main = "missing";
				} else {
					main = "level_" + warnLevel.lowerCaseName;
				}
			} else {
				main = split[0];

				if (warnLevel == QuiltTreeWarningLevel.NONE) {
					// Just to add a gap
					decors.add(null);
				} else {
					decors.add("level_" + warnLevel.lowerCaseName);
				}

				for (int i = 1; i < split.length && i < 3; i++) {
					decors.add(split[i]);
				}
			}

			return new IconInfo(main, decors.toArray(new String[0]));
		}

		@Override
		public int hashCode() {
			return hash;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) {
				return true;
			}

			if (obj == null || obj.getClass() != getClass()) {
				return false;
			}

			IconInfo other = (IconInfo) obj;
			return mainPath.equals(other.mainPath) && Arrays.equals(decor, other.decor);
		}
	}

	private static final class CustomTreeCellRenderer extends DefaultTreeCellRenderer {
		private static final long serialVersionUID = -5621219150752332739L;

		private final IconSet iconSet;

		private CustomTreeCellRenderer(IconSet icons) {
			this.iconSet = icons;
			//setVerticalTextPosition(TOP); // Move icons to top rather than centre
		}

		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
				boolean leaf, int row, boolean hasFocus) {
			super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

			setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

			if (value instanceof CustomTreeNode) {
				CustomTreeNode c = (CustomTreeNode) value;
				setIcon(iconSet.get(c.getIconInfo()));

				if (c.node.details == null || c.node.details.isEmpty()) {
					setToolTipText(null);
				} else {
					setToolTipText(applyWrapping(c.node.details));
				}
			}

			return this;
		}
	}

	private static String applyWrapping(String str) {
		if (str.indexOf('\n') < 0) {
			return str;
		}

		str = str.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\n", "<br>");

		return "<html>" + str + "</html>";
	}

	static class CustomTreeNode implements TreeNode {
		public final TreeNode parent;
		public final QuiltStatusNode node;
		public final List<CustomTreeNode> displayedChildren = new ArrayList<>();
		private IconInfo iconInfo;

		CustomTreeNode(TreeNode parent, QuiltStatusNode node, QuiltTreeWarningLevel minimumWarningLevel) {
			this.parent = parent;
			this.node = node;

			for (QuiltStatusNode c : node.children) {
				if (minimumWarningLevel.isHigherThan(c.getMaximumWarningLevel())) {
					continue;
				}

				displayedChildren.add(new CustomTreeNode(this, c, minimumWarningLevel));
			}
		}

		public IconInfo getIconInfo() {
			if (iconInfo == null) {
				iconInfo = IconInfo.fromNode(node);
			}

			return iconInfo;
		}

		@Override
		public String toString() {
			return applyWrapping(StringUtil.wrapLines(node.name, 120));
		}

		@Override
		public TreeNode getChildAt(int childIndex) {
			return displayedChildren.get(childIndex);
		}

		@Override
		public int getChildCount() {
			return displayedChildren.size();
		}

		@Override
		public TreeNode getParent() {
			return parent;
		}

		@Override
		public int getIndex(TreeNode node) {
			return displayedChildren.indexOf(node);
		}

		@Override
		public boolean getAllowsChildren() {
			return !isLeaf();
		}

		@Override
		public boolean isLeaf() {
			return displayedChildren.isEmpty();
		}

		@Override
		public Enumeration<CustomTreeNode> children() {
			return new Enumeration<CustomTreeNode>() {
				Iterator<CustomTreeNode> it = displayedChildren.iterator();

				@Override
				public boolean hasMoreElements() {
					return it.hasNext();
				}

				@Override
				public CustomTreeNode nextElement() {
					return it.next();
				}
			};
		}
	}
}
