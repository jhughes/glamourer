package io.huze.glamourer.ui;

import java.util.concurrent.CompletableFuture;
import io.huze.glamourer.Config;
import io.huze.glamourer.glam.Glamourer;
import io.huze.glamourer.glam.IconService;
import io.huze.glamourer.plate.Plate;
import io.huze.glamourer.plate.PlateManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import javax.annotation.Nonnull;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

@Slf4j
public class ImportPlateDialog extends JDialog
{
	@Nonnull
	private final Config config;
	@Nonnull
	private final PlateManager plateManager;
	@Nonnull
	private final Glamourer glamourer;
	private final IconService iconService;
	private final float iconScale;

	private final JTextArea textArea;
	private final JPanel previewPanel;
	private final JLabel errorLabel;
	private final JLabel warningLabel;
	private final JButton importButton;
	private final JButton overwriteButton;

	private String jsonText;
	@Getter
	private boolean imported;
	@Getter
	private boolean overwritten;

	public ImportPlateDialog(@Nonnull Window owner,
							 @Nonnull Config config,
							 @Nonnull PlateManager plateManager,
							 float iconScale)
	{
		super(owner, "Import Plate", ModalityType.APPLICATION_MODAL);
		this.config = config;
		this.plateManager = plateManager;
		this.glamourer = plateManager.getGlamourer();
		this.iconService = plateManager.getIconService();
		this.iconScale = iconScale;

		setLayout(new BorderLayout(0, 5));
		getRootPane().setBorder(new EmptyBorder(8, 8, 8, 8));
		getContentPane().setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// Text area for pasting JSON
		textArea = new JTextArea(5, 40);
		textArea.setLineWrap(true);
		textArea.setWrapStyleWord(true);

		// Right-click paste menu
		JPopupMenu textAreaPopup = new JPopupMenu();
		JMenuItem pasteItem = new JMenuItem("Paste");
		pasteItem.addActionListener(e -> {
			try
			{
				String clipboard = (String) Toolkit.getDefaultToolkit()
					.getSystemClipboard().getData(DataFlavor.stringFlavor);
				textArea.replaceSelection(clipboard);
			}
			catch (Exception ex)
			{
				log.debug("Failed to paste from clipboard", ex);
			}
		});
		textAreaPopup.add(pasteItem);
		textArea.setComponentPopupMenu(textAreaPopup);

		JScrollPane textScroll = new JScrollPane(textArea);
		textScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		textScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		JPanel topPanel = new JPanel(new BorderLayout());
		topPanel.setOpaque(false);
		JLabel pasteLabel = new JLabel("Paste plate JSON:");
		pasteLabel.setForeground(Color.WHITE);
		pasteLabel.setBorder(new EmptyBorder(0, 0, 4, 0));
		topPanel.add(pasteLabel, BorderLayout.NORTH);
		topPanel.add(textScroll, BorderLayout.CENTER);
		add(topPanel, BorderLayout.NORTH);

		// Preview panel (middle)
		JPanel centerPanel = new JPanel(new BorderLayout());
		centerPanel.setOpaque(false);

		errorLabel = new JLabel();
		errorLabel.setForeground(new Color(255, 80, 80));
		errorLabel.setBorder(new EmptyBorder(4, 0, 4, 0));
		errorLabel.setVisible(false);
		centerPanel.add(errorLabel, BorderLayout.SOUTH);

		VerticalScrollPane previewScroll = new VerticalScrollPane();
		previewPanel = previewScroll.getContainer();
		centerPanel.add(previewScroll, BorderLayout.CENTER);

		add(centerPanel, BorderLayout.CENTER);

		// Bottom panel: warning label + buttons
		JPanel bottomPanel = new JPanel(new BorderLayout());
		bottomPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		bottomPanel.setBorder(new EmptyBorder(8, 0, 0, 0));

		warningLabel = new JLabel();
		warningLabel.setForeground(new Color(255, 170, 0));
		warningLabel.setBorder(new EmptyBorder(4, 4, 4, 0));
		warningLabel.setVisible(false);
		bottomPanel.add(warningLabel, BorderLayout.NORTH);

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		buttonPanel.setOpaque(false);

		overwriteButton = new JButton("Overwrite");
		overwriteButton.setVisible(false);
		overwriteButton.addActionListener(e -> doImport());

		importButton = new JButton("Import");
		importButton.setEnabled(false);
		importButton.addActionListener(e -> doImportAsNew());

		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(e -> dispose());

		buttonPanel.add(overwriteButton);
		buttonPanel.add(importButton);
		buttonPanel.add(cancelButton);
		bottomPanel.add(buttonPanel, BorderLayout.CENTER);

		add(bottomPanel, BorderLayout.SOUTH);

		// Listen for text changes
		textArea.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				parseAndPreview();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				parseAndPreview();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				parseAndPreview();
			}
		});

		pack();
		setMinimumSize(new Dimension(350, 350));
		DialogUtil.positionNearCursor(this);
	}

	private void parseAndPreview()
	{
		String text = textArea.getText().trim();
		if (text.isEmpty())
		{
			clearPreview();
			return;
		}

		CompletableFuture<Plate> preview;
		try
		{
			preview = plateManager.loadImportPreview(text);
		}
		catch (Exception e)
		{
			showError(e.getMessage());
			return;
		}

		jsonText = text;
		importButton.setEnabled(false);
		errorLabel.setVisible(false);

		preview
			.thenAccept(plate -> SwingUtilities.invokeLater(() -> showPreview(plate)))
			.exceptionally(e -> {
				log.error("Failed to load plate for preview", e);
				SwingUtilities.invokeLater(() -> showError("Failed to load plate: " + e.getMessage()));
				return null;
			});
	}

	private void clearPreview()
	{
		jsonText = null;
		previewPanel.removeAll();
		previewPanel.setVisible(false);
		errorLabel.setVisible(false);
		warningLabel.setVisible(false);
		overwriteButton.setVisible(false);
		importButton.setEnabled(false);
	}

	private void showError(String message)
	{
		jsonText = null;
		previewPanel.removeAll();
		previewPanel.setVisible(false);
		warningLabel.setVisible(false);
		overwriteButton.setVisible(false);
		errorLabel.setText(message);
		errorLabel.setVisible(true);
		importButton.setEnabled(false);
	}

	private void showPreview(Plate previewPlate)
	{
		errorLabel.setVisible(false);
		previewPanel.removeAll();

		PlateRowPanel rowPanel = new PlateRowPanel(previewPlate, iconService, glamourer, config, iconScale, () -> {
			previewPanel.revalidate();
			previewPanel.repaint();
		});
		rowPanel.setExpanded(true);
		previewPanel.add(rowPanel);

		previewPanel.setVisible(true);

		int failedCount = previewPlate.getFailedGlamours().size();
		boolean duplicate = previewPlate.getId() != null && plateManager.hasPlateWithId(previewPlate.getId());

		StringBuilder warning = new StringBuilder("<html>");
		if (failedCount > 0)
		{
			warning.append(failedCount).append(" glamour").append(failedCount > 1 ? "s" : "")
				.append(" failed to load and is not shown.");
		}
		if (duplicate)
		{
			if (failedCount > 0)
			{
				warning.append("<br>");
			}
			warning.append("A plate with this ID already exists. Overwrite or import a new plate?");
		}
		warning.append("</html>");

		if (failedCount > 0 || duplicate)
		{
			warningLabel.setText(warning.toString());
			warningLabel.setVisible(true);
		}
		else
		{
			warningLabel.setVisible(false);
		}
		overwriteButton.setVisible(duplicate);
		overwriteButton.setEnabled(duplicate);

		importButton.setEnabled(true);
		previewPanel.revalidate();
		previewPanel.repaint();
		int maxHeight = (int) (Toolkit.getDefaultToolkit().getScreenSize().height * 0.90);
		getContentPane().setVisible(false);
		pack();
		if (getHeight() > maxHeight)
		{
			setSize(getWidth(), maxHeight);
		}
		getContentPane().setVisible(true);
	}

	private void doImport()
	{
		if (jsonText != null)
		{
			imported = true;
			overwritten = true;
			plateManager.importPlate(jsonText);
			dispose();
		}
	}

	private void doImportAsNew()
	{
		if (jsonText != null)
		{
			imported = true;
			plateManager.importPlateAsNew(jsonText);
			dispose();
		}
	}
}
