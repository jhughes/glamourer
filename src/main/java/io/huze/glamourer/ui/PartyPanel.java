package io.huze.glamourer.ui;

import io.huze.glamourer.Config;
import io.huze.glamourer.glam.Glamour;
import io.huze.glamourer.glam.GlamourVisibility;
import io.huze.glamourer.glam.IconService;
import io.huze.glamourer.party.PartyInterface;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.Box;
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
	private final IconService iconService;
	private final Set<Long> expandedMembers = new HashSet<>();
	private final JPanel toolbarButtons;
	private final ToggleButton settingsToggleButton;
	private final JPanel settingsPanel;
	private final ToggleSwitch sendToggle;
	private final ToggleSwitch recvToggle;
	private final JPanel contentPanel;
	private final JPanel membersContainer;
	private final PluginErrorPanel infoPanel;

	public PartyPanel(PartyInterface partyInterface, Config config, IconService iconService)
	{
		this.partyInterface = partyInterface;
		this.config = config;
		this.iconService = iconService;

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

		sendToggle = new ToggleSwitch(config.partySyncSend());
		settingsPanel.add(createToggleRow(Config.NAME_PARTY_SYNC_SEND,
			Config.DESC_PARTY_SYNC_SEND,
			sendToggle,
			config::setPartySyncSend));

		recvToggle = new ToggleSwitch(config.partySyncReceive());
		settingsPanel.add(createToggleRow(Config.NAME_PARTY_SYNC_RECV,
			Config.DESC_PARTY_SYNC_RECV,
			recvToggle,
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
		VerticalScrollPane scrollPane = new VerticalScrollPane();
		scrollPane.getContainer().add(contentPanel);
		centerPanel.add(scrollPane, BorderLayout.CENTER);
		add(centerPanel, BorderLayout.CENTER);

		// Register for state changes
		partyInterface.setOnStateChanged(() -> SwingUtilities.invokeLater(this::rebuildMembers));

		rebuildMembers();
	}

	private JPanel createToggleRow(String label, String tooltip, ToggleSwitch toggle, Consumer<Boolean> onChange)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(2, 0, 2, 0));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

		JLabel nameLabel = new JLabel(label);
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setToolTipText(tooltip);
		row.add(nameLabel, BorderLayout.WEST);

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
		sendToggle.setSelected(config.partySyncSend());
		recvToggle.setSelected(config.partySyncReceive());
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
		JPanel section = new JPanel(new BorderLayout())
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		section.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		section.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
			new EmptyBorder(4, 4, 4, 4)
		));

		JPanel headerRow = new JPanel(new BorderLayout(2, 0));
		headerRow.setOpaque(false);

		final boolean expanded = expandedMembers.contains(member.memberId);
		JButton expandButton = new JButton();
		ImageIcons.setExpandIcon(expandButton, expanded);
		expandButton.addActionListener(e -> {
			if (!expandedMembers.remove(member.memberId))
			{
				expandedMembers.add(member.memberId);
			}
			rebuildMembers();
		});
		headerRow.add(expandButton, BorderLayout.WEST);

		JLabel nameLabel = new JLabel(member.displayName);
		nameLabel.setForeground(member.hasGlamourer ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		headerRow.add(nameLabel, BorderLayout.CENTER);

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

			headerRow.add(rightPanel, BorderLayout.EAST);
		}

		section.add(headerRow, BorderLayout.NORTH);

		if (expanded)
		{
			section.add(createMemberGlamours(member.memberId), BorderLayout.CENTER);
		}

		return section;
	}

	/// The member's synced glamours, one read-only row each: the glamoured icon and the item name.
	private JPanel createMemberGlamours(long memberId)
	{
		JPanel list = new JPanel();
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setOpaque(false);
		list.setBorder(new EmptyBorder(4, 4, 0, 0));

		var glamours = partyInterface.getMemberGlamours(memberId);
		if (glamours.isEmpty())
		{
			JLabel empty = new JLabel("No synced glamours");
			empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			list.add(empty);
			return list;
		}

		final float iconScale = config.iconScale() / 100f;
		for (Glamour glamour : glamours)
		{
			if (list.getComponentCount() > 0)
			{
				list.add(Box.createVerticalStrut(3));
			}

			JLabel itemName = new JLabel(glamour.getItemName());
			itemName.setForeground(Color.WHITE);

			JPanel row = new JPanel(new BorderLayout(5, 0));
			row.setBackground(ColorScheme.DARK_GRAY_COLOR);
			row.setBorder(new EmptyBorder(3, 3, 3, 3));

			JLabel icon = new JLabel();
			ImageIcons.setScaledIcon(icon, iconService.getIcon(glamour), iconScale);
			row.add(icon, BorderLayout.WEST);
			row.add(itemName, BorderLayout.CENTER);
			row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
			list.add(row);
		}
		return list;
	}

}
