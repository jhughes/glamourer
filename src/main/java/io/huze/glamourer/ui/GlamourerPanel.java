package io.huze.glamourer.ui;

import io.huze.glamourer.Config;
import io.huze.glamourer.plate.PlateManager;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.net.URI;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import io.huze.glamourer.plate.Plate;

@Slf4j
public class GlamourerPanel extends JPanel
{
	@Getter
	private final PlateManagerPanel plateManagerPanel;

	private final JPanel toolbarSlot;
	private final JPanel subHeaderSlot;
	private final CardLayout contentCards;
	private final JPanel contentPanel;
	private GlamourerSubPanel activeSubPanel;

	public GlamourerPanel(PlateManager plateManager, Config config, Consumer<Plate> onAddItemRequest)
	{
		setLayout(new BorderLayout());

		// Create content panels first so they can be referenced in listeners
		plateManagerPanel = new PlateManagerPanel(plateManager, config, onAddItemRequest);

		// --- Title bar ---
		JPanel titlePanel = new JPanel(new BorderLayout());
		titlePanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		titlePanel.setBorder(new EmptyBorder(4, 6, 2, 4));

		// Left: title, discord
		JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		leftPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel titleLabel = new JLabel("Glamourer");
		titleLabel.setForeground(Color.WHITE);
		leftPanel.add(titleLabel);

		JButton discordButton = new JButton();
		ImageIcons.setDiscordIcon(discordButton);
		discordButton.setToolTipText("Join Discord");
		discordButton.addActionListener(e -> {
			try
			{
				Desktop.getDesktop().browse(new URI("https://discord.gg/B6dD9R5U36"));
			}
			catch (Exception ex)
			{
				log.warn("Failed to open Discord link", ex);
			}
		});
		leftPanel.add(discordButton);

		titlePanel.add(leftPanel, BorderLayout.WEST);

		// Right: slot for active panel's toolbar buttons
		toolbarSlot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		toolbarSlot.setBackground(ColorScheme.DARK_GRAY_COLOR);
		titlePanel.add(toolbarSlot, BorderLayout.EAST);

		// Sub-header slot (for sliders, warnings, etc.)
		subHeaderSlot = new JPanel();
		subHeaderSlot.setLayout(new BoxLayout(subHeaderSlot, BoxLayout.Y_AXIS));

		// North: title bar + sub-header
		JPanel northPanel = new JPanel();
		northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.Y_AXIS));
		northPanel.add(titlePanel);
		northPanel.add(subHeaderSlot);
		add(northPanel, BorderLayout.NORTH);

		// --- Content card layout ---
		contentCards = new CardLayout();
		contentPanel = new JPanel(contentCards);
		contentPanel.add(plateManagerPanel, plateManagerPanel.getCardKey());
		add(contentPanel, BorderLayout.CENTER);

		// Default: show plates
		activatePanel(plateManagerPanel);
	}

	public void onActivate()
	{
		activeSubPanel.onActivate();
	}

	private void activatePanel(GlamourerSubPanel panel)
	{
		activeSubPanel = panel;

		toolbarSlot.removeAll();
		subHeaderSlot.removeAll();

		var toolbar = panel.getToolbarButtons();
		if (toolbar != null)
		{
			toolbarSlot.add(toolbar);
		}
		var subHeader = panel.getSubHeaderPanel();
		if (subHeader != null)
		{
			subHeaderSlot.add(subHeader);
		}

		contentCards.show(contentPanel, panel.getCardKey());
		panel.onActivate();

		toolbarSlot.revalidate();
		toolbarSlot.repaint();
		subHeaderSlot.revalidate();
		subHeaderSlot.repaint();
	}

	public void showError(Exception ex)
	{
		ExceptionPanel exceptionPanel = new ExceptionPanel(ex);
		contentPanel.add(exceptionPanel, exceptionPanel.getCardKey());
		activatePanel(exceptionPanel);
	}
}
