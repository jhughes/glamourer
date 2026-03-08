package io.huze.glamourer.ui.texturepicker;

import io.huze.glamourer.texture.TextureReplacement;
import io.huze.glamourer.ui.DialogUtil;
import io.huze.glamourer.ui.ImageIcons;
import io.huze.glamourer.ui.colorpicker.ColorLabel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import lombok.Setter;

public class TextureLabel extends JPanel
{
	private static final int ICON_SIZE = ColorLabel.ROW_HEIGHT;

	@Setter
	private Consumer<Short> onTextureChange;
	private final String pickerName;
	private final TextureReplacement textureReplacement;
	private final JLabel textureDisplay;
	private final JButton revertButton;
	private JDialog activeDialog;

	public TextureLabel(String name, TextureReplacement textureReplacement)
	{
		this.pickerName = name;
		this.textureReplacement = textureReplacement;

		setLayout(new BorderLayout(3, 0));
		setOpaque(false);

		textureDisplay = new JLabel();
		textureDisplay.setOpaque(true);
		textureDisplay.setBackground(Color.BLACK);
		textureDisplay.setBorder(null);
		textureDisplay.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		textureDisplay.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				showPicker();
			}
		});
		add(textureDisplay, BorderLayout.CENTER);

		revertButton = new JButton()
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				BufferedImage origImg = TexturePicker.getTextureImage(textureReplacement.getOriginal());
				if (origImg != null)
				{
					g.drawImage(origImg, 0, 0, getWidth(), getHeight(), null);
				}
				super.paintComponent(g);
			}
		};
		revertButton.setPreferredSize(new Dimension(ICON_SIZE, ICON_SIZE));
		revertButton.setToolTipText("Double-click to revert to original texture");
		revertButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		revertButton.setBorderPainted(false);
		revertButton.setFocusPainted(false);
		revertButton.setContentAreaFilled(false);
		ImageIcons.setResetIcon(revertButton, Color.DARK_GRAY);
		revertButton.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() == 2)
				{
					revertToOriginal();
				}
			}
		});
		add(revertButton, BorderLayout.EAST);

		updateDisplay();
	}

	private void updateDisplay()
	{
		BufferedImage img = TexturePicker.getTextureImage(textureReplacement.getReplacement());
		if (img != null)
		{
			textureDisplay.setIcon(new ImageIcon(img.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH)));
		}
		textureDisplay.setText("Texture " + textureReplacement.getReplacement());
		textureDisplay.setForeground(Color.WHITE);

		revertButton.setVisible(textureReplacement.hasChanged());
		revertButton.repaint();
	}

	private void showPicker()
	{
		if (activeDialog != null && activeDialog.isVisible())
		{
			activeDialog.toFront();
			return;
		}

		final short initialTexture = textureReplacement.getReplacement();
		TexturePicker picker = new TexturePicker(initialTexture);
		activeDialog = DialogUtil.showModelessDialogNearCursor(
			this,
			picker,
			pickerName,
			picker.getSizeControls(),
			d -> {
				short selected = picker.getSelectedTextureId();
				if (selected != initialTexture)
				{
					textureReplacement.setReplacement(selected);
					updateDisplay();
					if (onTextureChange != null)
					{
						onTextureChange.accept(selected);
					}
				}
			}
		);
		activeDialog.setResizable(true);
		activeDialog.addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosed(WindowEvent e)
			{
				activeDialog = null;
			}
		});
	}

	private void revertToOriginal()
	{
		if (textureReplacement.hasChanged())
		{
			textureReplacement.setReplacement(textureReplacement.getOriginal());
			updateDisplay();
			if (onTextureChange != null)
			{
				onTextureChange.accept(textureReplacement.getReplacement());
			}
		}
	}

	public void setViewOnly()
	{
		textureDisplay.setCursor(Cursor.getDefaultCursor());
		revertButton.setCursor(Cursor.getDefaultCursor());
		revertButton.setToolTipText(null);
		revertButton.setIcon(null);
		revertButton.setRolloverIcon(null);
		for (var listener : textureDisplay.getMouseListeners())
		{
			textureDisplay.removeMouseListener(listener);
		}
	}
}
