package io.huze.glamourer.ui;

import io.huze.glamourer.Config;
import io.huze.glamourer.glam.Glamour;
import io.huze.glamourer.glam.ModelViewService;
import io.huze.glamourer.model.ModelChoice;
import io.huze.glamourer.plate.Plate;
import io.huze.glamourer.plate.PlateManager;
import io.huze.glamourer.render.ModelRenderer;
import io.huze.glamourer.render.OrbitView;
import io.huze.glamourer.texture.TextureImages;
import io.huze.glamourer.ui.colorpicker.ColorDisplay;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.Timer;
import javax.swing.UIManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.ColorJButton;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;

public class ItemGlamourFrame extends JDialog
{
	private static final Dimension MINIMUM_SIZE = new Dimension(420, 300);
	private static final int MINIMUM_COLORS_WIDTH = 150;
	private static final int MINIMUM_MODEL_WIDTH = 160;
	private static final int DIVIDER_WIDTH = 6;
	private static final int FULL_SHADE = 100;
	private static final int SHADE_SLIDER_WIDTH = 80;
	private static final int BACKGROUND_BUTTON_SIZE = 18;
	private static final int LABEL_GAP = 4;
	private static final int VIEW_CONTROL_GAP = 12;
	/// Reads queue on the client thread, so a drag settles first.
	private static final int MODEL_SETTLE_MS = 120;
	private static final int LAYOUT_SETTLE_MS = 500;

	@Nonnull
	private final ModelViewService modelViewService;
	@Nonnull
	private final Config config;
	@Nonnull
	private final ConfigManager configManager;
	@Nonnull
	private final ColorPickerManager colorPickerManager;
	@Nonnull
	private final Plate plate;
	@Nonnull
	private final Glamour glamour;
	@Nonnull
	private final ModelViewPanel modelView;
	@Nonnull
	private final GlamourEditorPanel editor;
	private JSplitPane split;

	private JSlider shadeSlider;
	private ColorJButton backgroundButton;
	private final Timer modelSettleTimer = new Timer(MODEL_SETTLE_MS, event -> showModel());
	private final Timer layoutSettleTimer = new Timer(LAYOUT_SETTLE_MS, event -> saveLayout());
	private boolean mirroringLight;
	private final Runnable onLightChanged = () -> {
		mirroringLight = true;
		try
		{
			shadeSlider.setValue((int) Math.round(ColorDisplay.getLightIntensity() * FULL_SHADE));
		}
		finally
		{
			mirroringLight = false;
		}
		modelSettleTimer.restart();
	};

	private ModelChoice source = ModelChoice.ITEM;
	private int readGeneration;
	private final Map<ModelChoice, OrbitView> views = new HashMap<>();

	ItemGlamourFrame(@Nullable Window owner,
					 @Nonnull PlateManager plateManager,
					 @Nonnull Plate plate,
					 @Nonnull Glamour glamour,
					 @Nonnull ModelViewService modelViewService,
					 @Nonnull TextureImages textureImages,
					 @Nonnull Config config,
					 @Nonnull ConfigManager configManager,
					 @Nonnull Executor executor,
					 @Nonnull ModelRenderer modelRenderer,
					 @Nonnull ColorPickerManager colorPickerManager,
					 @Nonnull Runnable onSettingsChanged,
					 @Nonnull Runnable onClosed)
	{
		super(owner, titleOf(glamour, plate), ModalityType.MODELESS);
		this.plate = plate;
		this.glamour = glamour;
		this.modelViewService = modelViewService;
		this.config = config;
		this.configManager = configManager;
		this.colorPickerManager = colorPickerManager;
		this.modelSettleTimer.setRepeats(false);
		this.layoutSettleTimer.setRepeats(false);

		this.modelView = new ModelViewPanel(executor, modelRenderer);
		modelView.setBackground(config.previewBackground());
		this.editor = new GlamourEditorPanel(plate, glamour.getPrimaryItemId(), textureImages, config, false,
			modelSettleTimer::restart, modelView::setSelection);
		modelView.setOnPicked(editor::openPicker);
		modelView.setRowSelectionOf(editor::getRowSelection);

		setIconImages(ImageIcons.getWindowIcons());
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setMinimumSize(MINIMUM_SIZE);
		setSize(config.glamourWindowWidth(), config.glamourWindowHeight());
		setLocationRelativeTo(owner);

		var content = new JPanel(new BorderLayout());
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.add(createSplit(glamour, onSettingsChanged), BorderLayout.CENTER);
		setContentPane(content);
		UndoRedoKeys.install(getRootPane(), plateManager::undo, plateManager::redo);

		ColorDisplay.addLightIntensityListener(onLightChanged);
		addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent event)
			{
				layoutSettleTimer.restart();
			}
		});
		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosed(WindowEvent event)
			{
				modelSettleTimer.stop();
				if (layoutSettleTimer.isRunning())
				{
					layoutSettleTimer.stop();
					saveLayout();
				}
				ColorDisplay.removeLightIntensityListener(onLightChanged);
				editor.dispose();
				onClosed.run();
			}
		});

		showModel();
	}

	@Nonnull
	private static String titleOf(@Nonnull Glamour glamour, @Nonnull Plate plate)
	{
		return glamour.getItemName() + " - " + plate.getName();
	}

	void refresh()
	{
		setTitle(titleOf(glamour, plate));
		editor.rebuild();
		showModel();
	}

	@Nonnull
	Plate getPlate()
	{
		return plate;
	}

	void rebuildColors()
	{
		editor.rebuild();
	}

	private void saveLayout()
	{
		configManager.setConfiguration(Config.GROUP, Config.KEY_GLAMOUR_WINDOW_WIDTH, getWidth());
		configManager.setConfiguration(Config.GROUP, Config.KEY_GLAMOUR_WINDOW_HEIGHT, getHeight());
		int colorsWidth = split.getRightComponent().getWidth();
		if (colorsWidth > 0)
		{
			configManager.setConfiguration(Config.GROUP, Config.KEY_GLAMOUR_WINDOW_COLORS_WIDTH, colorsWidth);
		}
	}

	private JSplitPane createSplit(@Nonnull Glamour glamour, @Nonnull Runnable onSettingsChanged)
	{
		var modelPane = createModelPane(glamour);
		modelPane.setMinimumSize(new Dimension(MINIMUM_MODEL_WIDTH, 0));

		split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, modelPane, createColorsPane(onSettingsChanged));
		split.setBorder(null);
		split.setDividerSize(DIVIDER_WIDTH);
		split.setContinuousLayout(true);
		split.setResizeWeight(1);
		split.setBackground(ColorScheme.DARK_GRAY_COLOR);
		split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, event -> layoutSettleTimer.restart());
		return split;
	}

	private JPanel createModelPane(@Nonnull Glamour glamour)
	{
		var pane = new JPanel(new BorderLayout());
		pane.setBackground(ColorScheme.DARK_GRAY_COLOR);

		var sourceBar = createSourceBar(glamour);
		if (sourceBar != null)
		{
			pane.add(sourceBar, BorderLayout.NORTH);
		}
		pane.add(modelView, BorderLayout.CENTER);
		pane.add(createViewBar(), BorderLayout.SOUTH);
		return pane;
	}

	@Nullable
	private JPanel createSourceBar(@Nonnull Glamour glamour)
	{
		List<ModelChoice> choices = modelViewService.getChoices(glamour);
		return choices.size() < 2 ? null : new ModelSourceBar(choices, source, this::showSource);
	}

	private JPanel createViewBar()
	{
		var bar = createBar(VIEW_CONTROL_GAP);

		var resetButton = new JButton("Reset view");
		resetButton.setFont(FontManager.getRunescapeSmallFont());
		resetButton.setToolTipText("Reset the view to defaults");
		resetButton.addActionListener(e -> modelView.resetView());
		bar.add(resetButton);

		bar.add(labelled("Gizmo", "Toggle the rotation gizmo", createGizmoToggle(), new Insets(0, LABEL_GAP, 0, 0)));
		bar.add(labelled("Shade", "Shade intensity", createShadeSlider(), getSliderInsets()));
		bar.add(labelled("Background", "Preview background color", createBackgroundButton(), new Insets(0, LABEL_GAP, 0, 0)));
		return bar;
	}

	private static JPanel labelled(String text, String tooltip, JComponent control, Insets controlInsets)
	{
		var panel = new JPanel(new GridBagLayout());
		panel.setOpaque(false);
		var label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setToolTipText(tooltip);
		control.setToolTipText(tooltip);

		var constraints = new GridBagConstraints();
		constraints.gridx = 0;
		panel.add(label, constraints);
		constraints.gridx = 1;
		constraints.insets = controlInsets;
		panel.add(control, constraints);
		return panel;
	}

	private static Insets getSliderInsets()
	{
		var thumb = UIManager.getDimension("Slider.thumbSize");
		int halfThumb = (thumb != null ? thumb.width : 12) / 2;
		int focusWidth = UIManager.getInt("Slider.focusWidth");
		return new Insets(0, LABEL_GAP - halfThumb - focusWidth, 0, -focusWidth);
	}

	private ToggleSwitch createGizmoToggle()
	{
		var gizmoToggle = new ToggleSwitch(config.glamourGizmo());
		modelView.setGizmoEnabled(gizmoToggle.isSelected());
		gizmoToggle.addItemListener(e -> {
			modelView.setGizmoEnabled(gizmoToggle.isSelected());
			configManager.setConfiguration(Config.GROUP, Config.KEY_GLAMOUR_GIZMO, gizmoToggle.isSelected());
		});
		return gizmoToggle;
	}

	private ColorJButton createBackgroundButton()
	{
		backgroundButton = new ColorJButton("", modelView.getBackground());
		backgroundButton.setFocusable(false);
		backgroundButton.setPreferredSize(new Dimension(BACKGROUND_BUTTON_SIZE, BACKGROUND_BUTTON_SIZE));
		backgroundButton.addActionListener(e -> {
			var picker = colorPickerManager.create(this, backgroundButton.getColor(), "Preview background", true);
			picker.setLocationRelativeTo(backgroundButton);
			picker.setOnColorChange(this::setPreviewBackground);
			picker.setOnClose(color -> configManager.setConfiguration(Config.GROUP, Config.KEY_PREVIEW_BACKGROUND, color));
			picker.setVisible(true);
		});
		return backgroundButton;
	}

	void setPreviewBackground(@Nonnull Color color)
	{
		modelView.setBackground(color);
		backgroundButton.setColor(color);
	}

	private static JPanel createBar()
	{
		return createBar(4);
	}

	private static JPanel createBar(int gap)
	{
		var bar = new JPanel(new FlowLayout(FlowLayout.LEFT, gap, 4));
		bar.setBackground(ColorScheme.DARK_GRAY_COLOR);
		return bar;
	}

	private JSlider createShadeSlider()
	{
		shadeSlider = new JSlider(0, FULL_SHADE, (int) Math.round(ColorDisplay.getLightIntensity() * FULL_SHADE));
		shadeSlider.setOpaque(false);
		shadeSlider.setFocusable(false);
		shadeSlider.setBorder(BorderFactory.createEmptyBorder());
		shadeSlider.setPreferredSize(new Dimension(SHADE_SLIDER_WIDTH, shadeSlider.getPreferredSize().height));
		shadeSlider.addChangeListener(e -> {
			if (mirroringLight)
			{
				return;
			}
			ColorDisplay.setLightIntensity(shadeSlider.getValue() / (double) FULL_SHADE);
			if (!shadeSlider.getValueIsAdjusting())
			{
				configManager.setConfiguration(Config.GROUP, Config.KEY_SHADE_INTENSITY, shadeSlider.getValue());
			}
		});
		return shadeSlider;
	}

	private VerticalScrollPane createColorsPane(@Nonnull Runnable onSettingsChanged)
	{
		var scrollPane = new VerticalScrollPane();
		scrollPane.setPreferredSize(new Dimension(config.glamourWindowColorsWidth(), 0));
		scrollPane.setMinimumSize(new Dimension(MINIMUM_COLORS_WIDTH, 0));
		scrollPane.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		var settings = new ThresholdSlidersPanel(config, onSettingsChanged);
		var container = scrollPane.getContainer();
		container.add(new CollapsiblePanel("Color group settings", ImageIcons.getSlidersIcon(), settings, false));
		editor.setAlignmentX(Component.LEFT_ALIGNMENT);
		container.add(editor);
		return scrollPane;
	}

	private void showSource(@Nonnull ModelChoice candidate)
	{
		views.put(source, modelView.getView());
		source = candidate;
		modelView.setView(views.getOrDefault(candidate, OrbitView.INITIAL));
		showModel();
	}

	private void showModel()
	{
		Glamour glamour = editor.getGlamour();
		if (glamour == null)
		{
			dispose();
			return;
		}

		final int generation = ++readGeneration;
		modelViewService.readModel(source, glamour, ColorDisplay.getLightIntensity(), model -> {
			if (generation == readGeneration)
			{
				modelView.setModel(model, model == null ? "No model" : "");
			}
		});
	}
}
