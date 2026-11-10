package io.huze.glamourer.ui;

import io.huze.glamourer.model.ModelChoice;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

class ModelSourceBar extends JPanel
{
	private static final int GAP = 4;

	private final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, GAP, GAP));
	private final Map<ModelChoice, JToggleButton> buttonsByChoice = new LinkedHashMap<>();
	private final JComboBox<ModelChoice> dropdown;
	private final Consumer<ModelChoice> onSelect;
	private boolean syncing;

	ModelSourceBar(@Nonnull List<ModelChoice> choices, @Nonnull ModelChoice selected, @Nonnull Consumer<ModelChoice> onSelect)
	{
		this.onSelect = onSelect;
		setLayout(null);
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);

		var group = new ButtonGroup();
		for (ModelChoice choice : choices)
		{
			var button = new JToggleButton(choice.getLabel());
			button.setFont(FontManager.getRunescapeSmallFont());
			button.setToolTipText(choice.getLabel() + " model");
			button.setSelected(choice.equals(selected));
			button.addActionListener(e -> select(choice));
			group.add(button);
			buttons.add(button);
			buttonsByChoice.put(choice, button);
		}

		dropdown = new JComboBox<>(choices.toArray(new ModelChoice[0]));
		dropdown.setFont(FontManager.getRunescapeSmallFont());
		dropdown.setToolTipText("Model to show");
		dropdown.setSelectedItem(selected);
		dropdown.addActionListener(e -> select((ModelChoice) dropdown.getSelectedItem()));

		var dropdownRow = new JPanel(new FlowLayout(FlowLayout.LEFT, GAP, GAP));
		dropdownRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		dropdownRow.add(dropdown);
		add(buttons);
		add(dropdownRow);
	}

	private void select(@Nonnull ModelChoice choice)
	{
		if (syncing)
		{
			return;
		}
		syncing = true;
		try
		{
			buttonsByChoice.get(choice).setSelected(true);
			dropdown.setSelectedItem(choice);
		}
		finally
		{
			syncing = false;
		}
		onSelect.accept(choice);
	}

	@Override
	public void doLayout()
	{
		boolean fits = buttons.getPreferredSize().width <= getWidth();
		var shown = fits ? getComponent(0) : getComponent(1);
		var hidden = fits ? getComponent(1) : getComponent(0);
		hidden.setVisible(false);
		shown.setVisible(true);
		shown.setBounds(0, 0, getWidth(), getHeight());
	}

	@Override
	public Dimension getPreferredSize()
	{
		var buttonsSize = buttons.getPreferredSize();
		var dropdownSize = getComponent(1).getPreferredSize();
		return new Dimension(dropdownSize.width, Math.max(buttonsSize.height, dropdownSize.height));
	}

	@Override
	public Dimension getMinimumSize()
	{
		return getPreferredSize();
	}
}
