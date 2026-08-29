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
import java.awt.Desktop.Action;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
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
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

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
import javax.swing.tree.TreePath;

import org.quiltmc.loader.api.gui.QuiltLoaderGui;
import org.quiltmc.loader.api.gui.QuiltTreeNode;
import org.quiltmc.loader.api.gui.QuiltTreeNode.SortOrder;
import org.quiltmc.loader.api.gui.QuiltWarningLevel;
import org.quiltmc.loader.impl.gui.BasicWindow.BasicWindowChangeListener;
import org.quiltmc.loader.impl.gui.PluginIconImpl.BlankIcon;
import org.quiltmc.loader.impl.gui.PluginIconImpl.IconType;
import org.quiltmc.loader.impl.gui.QuiltStatusNode.TreeNodeListener;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.StringUtil;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
class BasicWindowUI {
	static BufferedImage missingImage = null;
	static Icon missingIcon = null;

	static void open(BasicWindow<?> window) throws Exception {
		QuiltUI.init();

		SwingUtilities.invokeAndWait(() -> {
			new BasicWindowUI(window).open();
		});
	}

	final JFrame swingFrame;
	final BasicWindow<?> quiltWindow;
	final IconSet icons = new IconSet();

	public BasicWindowUI(BasicWindow<?> quiltWindow) {
		this.quiltWindow = quiltWindow;
		swingFrame = new JFrame();
		swingFrame.setVisible(false);
		swingFrame.setTitle(quiltWindow.title);
		updateIcon();

		// TODO: change this back to normal after debugging
		swingFrame.setMinimumSize(new Dimension(1, 1));
		swingFrame.setPreferredSize(new Dimension(800, 480));
		swingFrame.setLocationByPlatform(true);
		swingFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		swingFrame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				quiltWindow.onClosedFuture.complete(null);
			}
		});

		Container contentPane = swingFrame.getContentPane();

		if (quiltWindow.mainText != null && !quiltWindow.mainText.isEmpty()) {
			JLabel errorLabel = new JLabel(quiltWindow.mainText);
			errorLabel.setHorizontalAlignment(SwingConstants.CENTER);
			Font font = errorLabel.getFont();
			errorLabel.setFont(font.deriveFont(font.getSize() * 2.0f));
			contentPane.add(errorLabel, BorderLayout.NORTH);
		}

		final JTabbedPane tabs;

		if (quiltWindow.singleTabOnly) {
			AbstractTab tab = quiltWindow.tabs.get(0);
			contentPane.add(createPanel(null, tab), BorderLayout.CENTER);

			tabs = null;
		} else {
			tabs = new JTabbedPane();
			contentPane.add(tabs, BorderLayout.CENTER);

			for (AbstractTab tab : quiltWindow.tabs) {
				PluginIconImpl icon = tab.icon;
				QuiltWarningLevel level = tab.level();
				if (level.icon() != null) {
					if (icon == null) {
						icon = PluginIconImpl.fromApi(level.icon());
					} else {
						icon = icon.withLevel(level);
					}
				}
				tabs.addTab(tab.text, icons.get(icon), createPanel(tabs, tab));
			}
		}

		JPanel buttons = new JPanel();
		contentPane.add(buttons, BorderLayout.SOUTH);
		buttons.setLayout(new FlowLayout(FlowLayout.TRAILING));

		if (!quiltWindow.buttons.isEmpty()) {
			for (QuiltJsonButton button : quiltWindow.buttons) {
				convertToJButton(buttons, button);
			}
		}

		quiltWindow.listeners.add(new BasicWindow.BasicWindowChangeListener() {
			@Override
			public void onButtonAdded(QuiltJsonButton button) {
				convertToJButton(buttons, button);
			}

			@Override
			public void onTitleChanged() {
				swingFrame.setTitle(quiltWindow.title);
			}

			@Override
			public void onIconChanged() {
				SwingUtilities.invokeLater(BasicWindowUI.this::updateIcon);
			}

			@Override
			public void onAddTab(AbstractTab tab) {
				if (tabs != null) {
					PluginIconImpl icon = tab.icon;
					QuiltWarningLevel level = tab.level();
					if (QuiltWarningLevel.NONE.compareTo(level) <= 0) {
						if (icon == null) {
							icon = PluginIconImpl.fromApi(level.icon());
						} else {
							icon = icon.withLevel(level);
						}
					}
					tabs.addTab(tab.text, icons.get(icon), createPanel(tabs, tab));
				}
			}
		});
	}

	private void updateIcon() {
		PluginIconImpl icon = quiltWindow.icon;
		try {
			List<BufferedImage> images = new ArrayList<>();
			for (int size : icons.getSizes(icon)) {
				images.add(icons.generateIcon(icon, size));
			}
			if (images.isEmpty()) {
				images.add(icons.generateIcon(icon, 16));
			}
			swingFrame.setIconImages(images);
			setTaskBarImage(images.get(images.size() - 1));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private JComponent createPanel(JTabbedPane tabContainer, AbstractTab tab) {
		final JComponent panel;
		if (tab instanceof MessagesTab) {
			panel = createMessagesPanel(icons, (MessagesTab) tab);
		} else if (tab instanceof TreeTab) {
			TreeTab tree = (TreeTab) tab;
			panel = createTreePanel(tree.rootNode, tree.visibilityLevel, icons);
		} else {
			throw new IllegalStateException("Unknown tab " + tab.getClass());
		}

		if (tabContainer == null) {
			return panel;
		}

		tab.listeners.add(new AbstractTab.TabChangeListener() {
			int index = quiltWindow.tabs.indexOf(tab);

			@Override
			public void onTextChanged() {
				tabContainer.setTitleAt(index, tab.text);
			}
		});

		return panel;
	}

	private void open() {
		swingFrame.pack();
		swingFrame.setVisible(true);
		swingFrame.requestFocus();
	}

	private void convertToJButton(JPanel addTo, QuiltJsonButton button) {
		JButton btn = new JButton(button.text, icons.get(button.icon));
		button.listeners.add(new QuiltJsonButton.QuiltButtonListener() {
			@Override
			public void onTextChanged() {
				btn.setText(button.text);
			}

			@Override
			public void onIconChanged() {
				btn.setIcon(icons.get(button.icon));
			}

			@Override
			public void onEnabledChanged() {
				btn.setEnabled(button.enabled);
				btn.setToolTipText(button.disabledText);
			}
		});

		addTo.add(btn);
		btn.addActionListener(event -> {

			switch (button.action) {
				case CONTINUE: {
					quiltWindow.onClosedFuture.complete(null);
					swingFrame.dispose();
					return;
				}
				case CLOSE: {
					quiltWindow.onClosedFuture.complete(null);
					swingFrame.dispose();
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

			// if (button.type == QuiltJsonGui.QuiltBasicButtonAction.CLICK_ONCE) btn.setEnabled(false);
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
			JOptionPane.showMessageDialog(swingFrame, "Failed to open '" + file + "'");
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
				JOptionPane.showMessageDialog(swingFrame, "Failed to open '" + file + "'");
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
			JOptionPane.showMessageDialog(swingFrame, "Failed to open '" + file + "'");
			e.printStackTrace();
		}
	}

	private void openFile(String file) {
		try {
			Desktop.getDesktop().open(new File(file));
		} catch (IOException | UnsupportedOperationException e) {
			JOptionPane.showMessageDialog(swingFrame, "Failed to open '" + file + "'");
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
			JOptionPane.showMessageDialog(swingFrame, "Failed to edit '" + file + "'");
			e.printStackTrace();
		}
	}

	private void openWebUrl(String url) {
		try {
			URI uri = new URI(url);
			Desktop.getDesktop().browse(uri);
		} catch (URISyntaxException | IOException | UnsupportedOperationException e) {
			JOptionPane.showMessageDialog(swingFrame, "Failed to open '" + url + "'");
			e.printStackTrace();
		}
	}

	private void copyClipboardText(String text) {
		try {
			StringSelection clipboard = new StringSelection(text);
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(clipboard, clipboard);
		} catch (IllegalStateException e) {
			JOptionPane.showMessageDialog(swingFrame, "Failed to paste clipboard text!");
			e.printStackTrace();
		}
	}

	private void copyClipboardFile(String file) {
		try {
			String text = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);
			StringSelection clipboard = new StringSelection(text);
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(clipboard, clipboard);
		} catch (IllegalStateException e) {
			JOptionPane.showMessageDialog(swingFrame, "Failed to paste clipboard text!");
			e.printStackTrace();
		} catch (IOException e) {
			JOptionPane.showMessageDialog(swingFrame, "Failed to open file to paste from: '" + file + "'!");
			e.printStackTrace();
		}
	}

	private JComponent createMessagesPanel(IconSet icons, MessagesTab tab) {
		JScrollPane pane = new JScrollPane();
		pane.setOpaque(true);
		pane.getVerticalScrollBar().setUnitIncrement(16);

		JPanel outerPanel = new JPanel();
		outerPanel.setLayout(new BorderLayout());
		outerPanel.setBackground(Color.WHITE);

		class TabManager implements MessagesTab.MessageTabListener {

			JPanel panel = null;

			@Override
			public void onMessageAdded(QuiltJsonGuiMessage message) {
				SwingUtilities.invokeLater(() -> addPanelForMessage(message));
			}

			void addPanelForMessage(QuiltJsonGuiMessage message) {
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
		}

		TabManager manager = new TabManager();
		tab.listeners.add(manager);
		for (QuiltJsonGuiMessage message : tab.messages) {
			manager.addPanelForMessage(message);
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

		JLabel icon = new JLabel(icons.get(message.icon, 32));
		icon.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		top.add(icon, BorderLayout.WEST);
		JLabel title = new JLabel(message.title);
		title.setFont(title.getFont().deriveFont(Font.BOLD));
		top.add(title, BorderLayout.CENTER);

		/* CONTAINER
		 * 
		 * [NORTH]
		 * | TOP
		 * | [NORTH] - ICON, TEXT
		 * | [CENTER]
		 * | [SOUTH]
		 * 
		 * [CENTER]
		 * | MESSAGES_CONTAINER
		 * | [CENTER]
		 * | | MESSAGE_1
		 * | | [NORTH] - {Message 1}
		 * | | [CENTER]
		 * | | | MESSAGE_2
		 * |
		 * | [SOUTH]
		 * | | TREE_PANEL
		 * | | 
		 * 
		 * 
		 * [SOUTH]
		 * | { Buttons }
		 * 
		 */

		JPanel messagesPanel = new JPanel();
		container.add(messagesPanel, BorderLayout.CENTER);

		AtomicReference<JPanel> currentOuterPanel = new AtomicReference<>();

		class MessageListener implements QuiltJsonGuiMessage.QuiltMessageListener {

			static final int FLAG_UPDATE_TITLE = 1 << 0;
			static final int FLAG_UPDATE_ICON = 1 << 1;
			static final int FLAG_UPDATE_DESC = 1 << 2;
			static final int FLAG_UPDATE_TREE = 1 << 4;

			int updateFlags = 0;
			String titleText = message.title;
			PluginIconImpl msgIcon = message.icon;
			String[] description = message.description.toArray(new String[0]);
			String[] additionalInfo = message.additionalInfo.toArray(new String[0]);
			QuiltStatusNode tree = message.treeNode;

			JComponent currentTreePanel = null;

			@Override
			public synchronized void onTitleChanged() {
				titleText = message.title;
				updateFlags |= FLAG_UPDATE_TITLE;
				SwingUtilities.invokeLater(this::update);
			}

			@Override
			public synchronized void onIconChanged() {
				msgIcon = message.icon;
				updateFlags |= FLAG_UPDATE_ICON;
				SwingUtilities.invokeLater(this::update);
			}

			@Override
			public synchronized void onDescriptionChanged() {
				description = message.description.toArray(new String[0]);
				updateFlags |= FLAG_UPDATE_DESC;
				SwingUtilities.invokeLater(this::update);
			}

			@Override
			public synchronized void onAdditionalInfoChanged() {
				additionalInfo = message.additionalInfo.toArray(new String[0]);
				updateFlags |= FLAG_UPDATE_DESC;
				SwingUtilities.invokeLater(this::update);
			}

			@Override
			public void onTreeNodeChanged() {
				tree = message.treeNode;
				updateFlags |= FLAG_UPDATE_TREE;
				SwingUtilities.invokeLater(this::update);
			}

			synchronized void update() {
				if ((updateFlags & FLAG_UPDATE_TITLE) != 0) {
					title.setText(titleText);
				}

				if ((updateFlags & FLAG_UPDATE_ICON) != 0) {
					icon.setIcon(icons.get(msgIcon, 32));
				}

				if ((updateFlags & FLAG_UPDATE_DESC) != 0) {
					populateDescInfo();
				}

				if ((updateFlags & FLAG_UPDATE_TREE) != 0) {
					populateTree();
				}

				updateFlags = 0;
			}

			void populateDescInfo() {
				if (currentOuterPanel.get() != null) {
					JPanel old = currentOuterPanel.getAndSet(null);
					messagesPanel.remove(old);
				}
				JPanel panel = new JPanel();
				panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
				currentOuterPanel.set(panel);
				messagesPanel.add(panel, BorderLayout.NORTH);

				for (String desc : description) {
					JLabel label = new JLabel(applyWrapping(desc));
					panel.add(label);
				}

				for (String info : additionalInfo) {
					JLabel label = new JLabel(applyWrapping(info));
					label.setFont(label.getFont().deriveFont(Font.ITALIC));
					panel.add(label);
				}

				messagesPanel.validate();
			}

			void populateTree() {
				if (currentTreePanel != null) {
					messagesPanel.remove(currentTreePanel);
					currentTreePanel = null;
				}

				if (tree != null) {
					currentTreePanel = createTreePanel(tree, QuiltWarningLevel.NONE, icons);
					messagesPanel.add(currentTreePanel, BorderLayout.SOUTH);
				}
			}
		}

		MessageListener listener = new MessageListener();

		listener.populateDescInfo();
		if (message.treeNode != null) {
			listener.populateTree();
		}

		message.listeners.add(listener);

		if (!message.buttons.isEmpty()) {
			JPanel buttons = new JPanel();
			buttons.setAlignmentY(0);
			buttons.setLayout(new FlowLayout(FlowLayout.LEADING));
			container.add(buttons, BorderLayout.SOUTH);

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

	private static JPanel createTreePanel(QuiltStatusNode rootNode, QuiltWarningLevel minimumWarningLevel,
		IconSet iconSet) {

		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		JTree tree = new JTree((DefaultTreeModel) null);
		if (rootNode.childNodesByAddition.isEmpty() && rootNode.childNodesByAlphabetical.isEmpty()) {
			// Swing doesn't seem to handle updates to an empty root node correctly
			// I assume this is because an invisible root node is *collapsed* by default?
			// (And the user has no way to change that)
			tree.setRootVisible(true);
		} else {
			tree.setRootVisible(false);
		}
		tree.setShowsRootHandles(true);
		tree.setRowHeight(0); // Allow rows to be multiple lines tall

		DefaultTreeModel model = new DefaultTreeModel(null);
		CustomTreeNode treeNode = new CustomTreeNode(tree, model, null, rootNode, minimumWarningLevel);
		model.setRoot(treeNode);

		tree.setModel(model);

		for (int row = 0; row < tree.getRowCount(); row++) {
			if (!tree.isVisible(tree.getPathForRow(row))) {
				continue;
			}

			CustomTreeNode node = ((CustomTreeNode) tree.getPathForRow(row).getLastPathComponent());

			if (node.node.getExpandByDefault()) {
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
		InputStream stream = BasicWindowUI.class.getResourceAsStream(str);

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

		/** The sizes of icons that quilt-loader ships. Stored largest-to-smallest. */
		private static final int[] ICON_SIZES = { 128, 16, 8 };

		/** Map of IconInfo -> Integer Size -> Real Icon. */
		private final Map<PluginIconImpl, Map<Integer, Icon>> icons = new HashMap<>();

		/** Map of Real Icon Type -> Integer Size -> Real Icon. */
		private final Map<PluginIconImpl.IconType, Map<Integer, BufferedImage>> rawIcons = new HashMap<>();
		private final Map<PluginIconImpl.UploadedIcon, NavigableMap<Integer, BufferedImage>> uploadedIcons = new HashMap<>();

		public Icon get(PluginIconImpl info) {
			return get(info, 16);
		}

		public Icon get(PluginIconImpl info, int scale) {
			if (info == null) {
				return null;
			}
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

		public BufferedImage get(PluginIconImpl.IconType info, int scale) {
			// TODO: HDPI

			Map<Integer, BufferedImage> map = rawIcons.computeIfAbsent(info, k -> new HashMap<>());

			BufferedImage icon = map.get(scale);

			if (icon == null) {
				try {
					icon = loadIcon(info, scale);
				} catch (IOException e) {
					e.printStackTrace();
					icon = missingImage();
				}

				map.put(scale, icon);
			}

			return icon;
		}

		Icon loadIcon(PluginIconImpl info, int scale) throws IOException {
			return new ImageIcon(generateIcon(info, scale));
		}

		BufferedImage loadIcon(PluginIconImpl.IconType info, int scale) throws IOException {
			return generateIcon(info, scale);
		}

		static class IconInfoData {
			final IconType main;
			final IconType[] sub;

			IconInfoData(IconType main, IconType[] sub) {
				this.main = main;
				this.sub = sub;
			}

			/** Normalises the icon */
			IconInfoData(PluginIconImpl icon) {
				IconType main = icon.icon;
				sub = Arrays.copyOf(icon.subIcons, 4);

				if (main instanceof BlankIcon) {
					if (sub[PluginIconImpl.BOTTOM_RIGHT] != null) {
						main = sub[PluginIconImpl.BOTTOM_RIGHT];
						sub[PluginIconImpl.BOTTOM_RIGHT] = null;
					} else if (sub[PluginIconImpl.BOTTOM_LEFT] != null) {
						main = sub[PluginIconImpl.BOTTOM_LEFT];
						sub[PluginIconImpl.BOTTOM_LEFT] = null;
					} else {
						for (int i = 2; i < 4; i++) {
							 if (sub[i] != null) {
								main = sub[i];
								sub[i] = null;
								break;
							 }
						}
						if (main instanceof BlankIcon) {
							main = GuiManagerImpl.ICON_TREE_DOT.icon;
						}
					}
				}

				this.main = main;
			}
		}

		BufferedImage generateIcon(PluginIconImpl info, int scale) throws IOException {
			BufferedImage img = new BufferedImage(scale, scale, BufferedImage.TYPE_INT_ARGB);
			Graphics2D imgG2d = img.createGraphics();

			IconInfoData fixed = new IconInfoData(info);

			IconType main = fixed.main;
			IconType[] sub = fixed.sub;

			BufferedImage mainImg = generateIcon(main, scale);
			if (mainImg != null) {
				imgG2d.drawImage(mainImg, 0, 0, scale, scale, null);
			}

			final int[][] coords = { { 0, scale / 2 }, { scale / 2, scale / 2 }, { scale / 2, 0 }, { 0, 0 } };

			for (int i = 0; i < 4; i++) {
				IconType decor = sub[i];

				if (decor == null) {
					continue;
				}

				BufferedImage decorImg = generateIcon(decor, scale);
				if (decorImg != null) {
					imgG2d.drawImage(decorImg, coords[i][0], coords[i][1], scale / 2, scale / 2, null);
				}
			}
			return img;
		}

		NavigableSet<Integer> getSizes(PluginIconImpl info) {
			NavigableSet<Integer> sizes = new TreeSet<>();
			addSizes(info.icon, sizes, 1);
			for (IconType sub : info.subIcons) {
				addSizes(sub, sizes, 2);
			}
			return sizes;
		}

		private void addSizes(IconType info, NavigableSet<Integer> sizes, int scale) {
			if (info == null) {
				return;
			}

			if (info instanceof PluginIconImpl.BuiltinIcon) {
				String path = ((PluginIconImpl.BuiltinIcon) info).path;
				if (path.startsWith("!")) {
					int iconId = Integer.parseInt(path.substring(1));
					for (Integer key : QuiltForkServerMain.getCustomIcon(iconId).keySet()) {
						sizes.add(scale * key);
					}
				}

				for (int size : ICON_SIZES) {
					String realPath = "/ui/icon/" + path + "_x" + size + ".png";
					try (InputStream stream = BasicWindowUI.class.getResourceAsStream(realPath)) {
						if (stream != null) {
							sizes.add(size * scale);
						}
					} catch (IOException e) {
						// Ignored
					}
				}
			} else if (info instanceof PluginIconImpl.UploadedIcon) {
				PluginIconImpl.UploadedIcon upl = (PluginIconImpl.UploadedIcon) info;

				NavigableMap<Integer, BufferedImage> map;
				try {
					map = loadUploadedIcons(upl);
				} catch (IOException e) {
					e.printStackTrace();
					return;
				}

				for (Integer key : map.keySet()) {
					sizes.add(scale * key);
				}

			} else if (info instanceof PluginIconImpl.LegacyUploadedIcon) {
				int index = ((PluginIconImpl.LegacyUploadedIcon) info).index;

				NavigableMap<Integer, BufferedImage> iconMap = QuiltForkServerMain.getCustomIcon(index);
				for (Integer key : iconMap.keySet()) {
					sizes.add(scale * key);
				}
			} else if (info instanceof PluginIconImpl.BlankIcon) {
				// Don't add sizes
			} else {
				throw new IllegalStateException("Unknown / new icon type " + info.getClass());
			}
		}

		BufferedImage generateIcon(PluginIconImpl.IconType info, int scale) throws IOException {
			BufferedImage img = new BufferedImage(scale, scale, BufferedImage.TYPE_INT_ARGB);
			if (info == null) {
				return img;
			}
			Graphics2D imgG2d = img.createGraphics();

			if (info instanceof PluginIconImpl.BuiltinIcon) {
				String path = ((PluginIconImpl.BuiltinIcon) info).path;
				BufferedImage main = loadImage(path, scale);
				if (main != null) {
					imgG2d.drawImage(main, 0, 0, scale, scale, null);
				}
			} else if (info instanceof PluginIconImpl.UploadedIcon) {
				PluginIconImpl.UploadedIcon upl = (PluginIconImpl.UploadedIcon) info;
				NavigableMap<Integer, BufferedImage> map = loadUploadedIcons(upl);

				Entry<Integer, BufferedImage> bestSource = map.ceilingEntry(scale);
				if (bestSource == null) {
					bestSource = map.floorEntry(scale);
				}

				if (bestSource != null) {
					imgG2d.drawImage(bestSource.getValue(), 0, 0, scale, scale, null);
				}

			} else if (info instanceof PluginIconImpl.LegacyUploadedIcon) {
				int index = ((PluginIconImpl.LegacyUploadedIcon) info).index;

				NavigableMap<Integer, BufferedImage> iconMap = QuiltForkServerMain.getCustomIcon(index);
				Entry<Integer, BufferedImage> bestSource = iconMap.ceilingEntry(scale);
				if (bestSource == null) {
					bestSource = iconMap.floorEntry(scale);
				}

				if (bestSource != null) {
					imgG2d.drawImage(bestSource.getValue(), 0, 0, scale, scale, null);
				}
			} else if (info instanceof PluginIconImpl.BlankIcon) {
				// Nothing to draw
			} else {
				throw new IllegalStateException("Unknown / new icon type " + info.getClass());
			}
			return img;
		}

		private NavigableMap<Integer, BufferedImage> loadUploadedIcons(PluginIconImpl.UploadedIcon upl)
			throws IOException {

			byte[][] srcImages = upl.imageBytes;

			NavigableMap<Integer, BufferedImage> map = uploadedIcons.computeIfAbsent(upl, u -> new TreeMap<>());

			if (map.isEmpty()) {
				for (byte[] src : srcImages) {
					BufferedImage sub = ImageIO.read(new ByteArrayInputStream(src));
					if (sub != null) {
						map.put(sub.getWidth(), sub);
					}
				}
			}
			return map;
		}

		BufferedImage loadImage(String path, int scale) throws IOException {
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

			// Get the largest image which is either equal to or smaller than the given scale

			for (int size : ICON_SIZES) {
				if (size > scale) {
					continue;
				}
				String realPath = "/ui/icon/" + path + "_x" + size + ".png";
				try (InputStream stream = BasicWindowUI.class.getResourceAsStream(realPath)) {
					if (stream == null) {
						continue;
					}
					return ImageIO.read(stream);
				}
			}

			throw new FileNotFoundException(path  + " at size " + scale);
		}
	}

	private static BufferedImage missingImage() {
		if (missingImage == null) {
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

			missingImage = img;
		}
		return missingImage;
	}

	private static Icon missingIcon() {
		if (missingIcon == null) {
			missingIcon = new ImageIcon(missingImage());
		}
		return missingIcon;
	}

	private static final class CustomTreeCellRenderer extends DefaultTreeCellRenderer {
		private static final long serialVersionUID = -5621219150752332739L;

		private final IconSet iconSet;

		private CustomTreeCellRenderer(IconSet icons) {
			this.iconSet = icons;
			// setVerticalTextPosition(TOP); // Move icons to top rather than centre
		}

		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
			boolean leaf, int row, boolean hasFocus) {
			super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

			setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

			if (value instanceof CustomTreeNode) {
				CustomTreeNode c = (CustomTreeNode) value;
				setIcon(iconSet.get(c.getIcon()));

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

		str = str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");

		return "<html>" + str + "</html>";
	}

	static class CustomTreeNode implements TreeNode {
		final JTree tree;
		final DefaultTreeModel model;
		public final TreeNode parent;
		public final QuiltStatusNode node;
		private final Map<QuiltStatusNode, CustomTreeNode> nodeToChild = new HashMap<>();
		public final List<CustomTreeNode> displayedChildren = new ArrayList<>();
		private PluginIconImpl iconInfo;

		CustomTreeNode(JTree tree, DefaultTreeModel model, TreeNode parent, QuiltStatusNode node, QuiltWarningLevel minimumWarningLevel) {
			this.tree = tree;
			this.model = model;
			this.parent = parent;
			this.node = node;

			for (QuiltStatusNode c : node.childIterable()) {
				if (minimumWarningLevel.compareTo(c.maximumLevel()) < 0) {
					continue;
				}

				CustomTreeNode child = new CustomTreeNode(tree, model, this, c, minimumWarningLevel);
				nodeToChild.put(c, child);
				displayedChildren.add(child);
			}

			node.listeners.add(new QuiltStatusNode.TreeNodeListener() {
				@Override
				public void onIconChanged() {
					model.nodeChanged(CustomTreeNode.this);
				}

				@Override
				public void onLevelChanged() {
					model.nodeChanged(CustomTreeNode.this);
				}

				@Override
				public void onMaxLevelChanged() {
					model.nodeChanged(CustomTreeNode.this);
				}

				@Override
				public void onTextChanged() {
					model.nodeChanged(CustomTreeNode.this);
				}

				@Override
				public void onChildAdded(QuiltStatusNode child, QuiltTreeNode.SortOrder sortOrder) {
					// There isn't a one-to-one mapping from TreeNode index to QuiltStatusNode index
					// due to the minimum warning level filter
					// So instead we just have to fully iterate the new child list
					// and get the new index that way

					if (minimumWarningLevel.compareTo(child.maximumLevel() ) < 0) {
						// It's not actually displayed, so nothing has changed
						return;
					}

					displayedChildren.clear();

					int[] index = { -1 };
					CustomTreeNode added = null;

					for (QuiltStatusNode c : node.childIterable()) {
						if (minimumWarningLevel.compareTo(c.maximumLevel()) < 0) {
							continue;
						}

						CustomTreeNode treeNode = nodeToChild.get(c);
						if (treeNode == null) {
							treeNode = new CustomTreeNode(tree, model, CustomTreeNode.this, c, minimumWarningLevel);
							nodeToChild.put(c, treeNode);
							added = treeNode;
							index[0] = displayedChildren.size();
						}
						displayedChildren.add(treeNode);
					}

					System.out.println("model.nodesWereInserted( " + index[0] + " )");
					model.nodesWereInserted(CustomTreeNode.this, index);

					if (node.parent == null) {
						tree.expandPath(new TreePath(new Object[] { CustomTreeNode.this, added }));
					}
				}
			});
		}

		public PluginIconImpl getIcon() {
			if (iconInfo == null) {
				if (node.icon == null) {
					if (node.level().icon() == null) {
						iconInfo = PluginIconImpl.fromApi(QuiltLoaderGui.iconTreeDot());
					} else {
						iconInfo = PluginIconImpl.fromApi(node.maximumLevel().icon());
					}
				} else {
					iconInfo = node.icon.withLevel(node.maximumLevel());
				}
			}

			return iconInfo;
		}

		@Override
		public String toString() {
			return applyWrapping(StringUtil.wrapLines(node.text, 120));
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
