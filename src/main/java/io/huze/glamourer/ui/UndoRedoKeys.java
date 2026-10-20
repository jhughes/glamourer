package io.huze.glamourer.ui;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.annotation.Nonnull;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class UndoRedoKeys
{
	private static final int MENU_SHORTCUT_MASK = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
	private static final String MODIFIER_NAME = InputEvent.getModifiersExText(MENU_SHORTCUT_MASK);
	public static final String UNDO_SHORTCUT_TEXT = MODIFIER_NAME + " + Z";
	public static final String REDO_SHORTCUT_TEXT = MODIFIER_NAME + " + Shift + Z  /  " + MODIFIER_NAME + " + Y";

	public static void install(@Nonnull JComponent component, @Nonnull Runnable undo, @Nonnull Runnable redo)
	{
		var inputMap = component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, MENU_SHORTCUT_MASK), "glamourerUndo");
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, MENU_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK), "glamourerRedo");
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, MENU_SHORTCUT_MASK), "glamourerRedo");
		component.getActionMap().put("glamourerUndo", action(undo));
		component.getActionMap().put("glamourerRedo", action(redo));
	}

	/// Deferred so the keystroke finishes before the plates rebuild under it.
	private static AbstractAction action(@Nonnull Runnable runnable)
	{
		return new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				SwingUtilities.invokeLater(runnable);
			}
		};
	}
}
