package io.huze.glamourer.ui;

import io.huze.glamourer.Config;
import io.huze.glamourer.glam.GlamourVisibility;
import io.huze.glamourer.party.PartyInterface;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.PluginErrorPanel;

public class PartyPanel extends JPanel implements GlamourerSubPanel
{
	private final PartyInterface partyInterface;
	private final Config config;
	private final JPanel toolbarButtons;
	private final ToggleButton settingsToggleButton;
	private final JPanel settingsPanel;
	private final JPanel contentPanel;
	private final JPanel membersContainer;
	private final PluginErrorPanel infoPanel;

	public PartyPanel(PartyInterface partyInterface, Config config)
	{
		this.partyInterface = partyInterface;
		this.config = config;

		setLayout(new BorderLayout());

		// --- Toolbar button: settings cog ---
		toolbarButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		toolbarButtons.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JButton resyncButton = new JButton();
		ImageIcons.setSyncIcon(resyncButton);
		resyncButton.setToolTipText("Resync glamours with all party members");
		resyncButton.addActionListener(e -> partyInterface.resync());
		toolbarButtons.add(resyncButton);

		settingsToggleButton = new ToggleButton();
		ImageIcons.setCogIcon(settingsToggleButton);
		settingsToggleButton.setToolTipText("Settings");
		toolbarButtons.add(settingsToggleButton);

		// --- Settings sub-header (initially hidden) ---
		settingsPanel = new JPanel();
		settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
		settingsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		settingsPanel.setBorder(new EmptyBorder(6, 8, 6, 8));
		settingsPanel.setVisible(false);

		settingsPanel.add(createToggleRow(Config.NAME_PARTY_SYNC_SEND,
			Config.DESC_PARTY_SYNC_SEND,
			config.partySyncSend(),
			config::setPartySyncSend));

		settingsPanel.add(createToggleRow(Config.NAME_PARTY_SYNC_RECV,
			Config.DESC_PARTY_SYNC_RECV,
			config.partySyncReceive(),
			config::setPartySyncReceive));

		settingsToggleButton.addActionListener(e -> {
			boolean visible = !settingsPanel.isVisible();
			settingsPanel.setVisible(visible);
			settingsToggleButton.setActive(visible);
		});

		// --- Info panel for empty states ---
		infoPanel = new PluginErrorPanel();
		infoPanel.setVisible(false);

		// --- Content: members list ---
		contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setBorder(new EmptyBorder(6, 6, 6, 6));

		JPanel membersSection = new JPanel(new BorderLayout());
		membersSection.setOpaque(false);

		JLabel membersLabel = new JLabel("Party Members");
		membersLabel.setForeground(Color.WHITE);
		membersLabel.setBorder(new EmptyBorder(0, 0, 4, 0));
		membersSection.add(membersLabel, BorderLayout.NORTH);

		membersContainer = new JPanel();
		membersContainer.setLayout(new BoxLayout(membersContainer, BoxLayout.Y_AXIS));
		membersContainer.setOpaque(false);
		membersSection.add(membersContainer, BorderLayout.CENTER);

		contentPanel.add(membersSection);

		JPanel centerPanel = new JPanel(new BorderLayout());
		centerPanel.add(infoPanel, BorderLayout.NORTH);
		centerPanel.add(contentPanel, BorderLayout.CENTER);
		add(centerPanel, BorderLayout.CENTER);

		// Register for state changes
		partyInterface.setOnStateChanged(() -> SwingUtilities.invokeLater(this::rebuildMembers));

		rebuildMembers();
	}

	private JPanel createToggleRow(String label, String tooltip, boolean initial, java.util.function.Consumer<Boolean> onChange)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(2, 0, 2, 0));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

		JLabel nameLabel = new JLabel(label);
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setToolTipText(tooltip);
		row.add(nameLabel, BorderLayout.WEST);

		ToggleSwitch toggle = new ToggleSwitch(initial);
		toggle.addItemListener(e -> onChange.accept(toggle.isSelected()));
		row.add(toggle, BorderLayout.EAST);

		return row;
	}

	@Override
	public String getCardKey()
	{
		return "PARTY";
	}

	@Override
	public JPanel getToolbarButtons()
	{
		return toolbarButtons;
	}

	@Nullable
	@Override
	public JPanel getSubHeaderPanel()
	{
		return settingsPanel;
	}

	@Override
	public void onActivate()
	{
		if (!config.partySyncSend() && !config.partySyncReceive())
		{
			settingsPanel.setVisible(true);
			settingsToggleButton.setActive(true);
		}
		rebuildMembers();
	}

	public void rebuildMembers()
	{
		membersContainer.removeAll();

		if (!partyInterface.isPartyPluginActive())
		{
			infoPanel.setContent("Party plugin disabled", "Enable the Party plugin to use Party Sync.");
			infoPanel.setVisible(true);
			contentPanel.setVisible(false);
		}
		else if (!partyInterface.isInParty())
		{
			infoPanel.setContent("Not in a party", "Use the Party plugin to join a party.");
			infoPanel.setVisible(true);
			contentPanel.setVisible(false);
		}
		else
		{
			var members = partyInterface.getMembers();
			if (members.isEmpty())
			{
				infoPanel.setContent("Party empty", "Invite others to join to use Party Sync.");
				infoPanel.setVisible(true);
				contentPanel.setVisible(false);
			}
			else
			{
				infoPanel.setVisible(false);
				contentPanel.setVisible(true);
				for (var member : members)
				{
					membersContainer.add(createMemberRow(member));
				}
			}
		}

		membersContainer.revalidate();
		membersContainer.repaint();
	}

	private JPanel createMemberRow(PartyInterface.PartyMemberInfo member)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
			new EmptyBorder(4, 4, 4, 4)
		));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

		JLabel nameLabel = new JLabel(member.displayName);
		nameLabel.setForeground(member.hasGlamourer ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		row.add(nameLabel, BorderLayout.WEST);

		{
			boolean hidden = partyInterface.isMemberHidden(member.memberId);
			boolean canToggle = member.hasGlamourer && member.hasName();

			JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
			rightPanel.setOpaque(false);

			GlamourVisibility visibility = !member.hasGlamourer ? GlamourVisibility.DISABLED
				: hidden ? GlamourVisibility.HIDDEN : GlamourVisibility.VISIBLE;
			JLabel eyeLabel = new JLabel(ImageIcons.getVisibilityIcon(visibility));
			eyeLabel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
			rightPanel.add(eyeLabel);

			ToggleSwitch toggle = new ToggleSwitch(canToggle && !hidden);
			if (canToggle)
			{
				toggle.addItemListener(e -> {
					boolean nowHidden = !toggle.isSelected();
					partyInterface.setMemberHidden(member.memberId, nowHidden);
					eyeLabel.setIcon(ImageIcons.getVisibilityIcon(
						nowHidden ? GlamourVisibility.HIDDEN : GlamourVisibility.VISIBLE));
				});
			}
			else
			{
				toggle.setEnabled(false);
				toggle.setToolTipText(!member.hasName() ? "Not Logged In" : "Sync Disabled");
			}
			rightPanel.add(toggle);

			row.add(rightPanel, BorderLayout.EAST);
		}

		return row;
	}

}
