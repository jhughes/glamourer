package io.huze.glamourer;

import io.huze.glamourer.color.Brightness;
import io.huze.glamourer.render.ModelPose;
import io.huze.glamourer.render.ModelRenderer;
import io.huze.glamourer.render.ModelSnapshot;
import io.huze.glamourer.render.OrbitView;
import io.huze.glamourer.render.PosedModel;
import io.huze.glamourer.texture.TextureProvider;
import io.huze.glamourer.ui.ModelViewPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.SpriteManager;
import net.runelite.cache.TextureManager;
import net.runelite.cache.definitions.FrameDefinition;
import net.runelite.cache.definitions.FramemapDefinition;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.SequenceDefinition;
import net.runelite.cache.definitions.SpotAnimDefinition;
import net.runelite.cache.definitions.loaders.FrameLoader;
import net.runelite.cache.definitions.loaders.FramemapLoader;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.definitions.loaders.SequenceLoader;
import net.runelite.cache.definitions.loaders.SpotAnimLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;

/// Developer tool: browses the local game cache's spotanims newest first and plays each one's
/// animation through the plugin's own renderer, so a new weapon's graphics can be found by eye.
///
/// Run with {@code ./gradlew spotAnimPreviewer}, or this main from the IDE. The cache defaults to
/// RuneLite's jagexcache; pass a directory to use another. {@code --dump <id or from-to> <dir> [turn°] [tilt°]}
/// writes a strip of animation frames per spotanim to PNG instead of opening the window.
public class SpotAnimPreviewer
{
	private static final int CANVAS = 512;
	private static final int CLIENT_TICK_MS = 20;
	private static final int DUMP_TILE = 256;
	private static final int DUMP_SUPERSAMPLE = 2;

	public static void main(String[] args) throws IOException
	{
		File cacheDir = new File(System.getProperty("user.home"), ".runelite/jagexcache/oldschool/LIVE");
		if (args.length >= 1 && !args[0].startsWith("--"))
		{
			cacheDir = new File(args[0]);
		}
		Cache cache = new Cache(cacheDir);
		if (args.length >= 3 && args[0].equals("--dump"))
		{
			String[] range = args[1].split("-");
			int from = Integer.parseInt(range[0]);
			int to = Integer.parseInt(range[range.length - 1]);
			double turnDegrees = args.length >= 4 ? Double.parseDouble(args[3]) : 0;
			double tiltDegrees = args.length >= 5 ? Double.parseDouble(args[4]) : 0;
			OrbitView view = OrbitView.INITIAL.turned(Math.toRadians(turnDegrees), Math.toRadians(tiltDegrees));
			for (int id = from; id <= to; id++)
			{
				dumpFrames(cache, id, new File(args[2]), view);
			}
			return;
		}
		SwingUtilities.invokeLater(() -> new PreviewWindow(cache).setVisible(true));
	}

	private static void dumpFrames(Cache cache, int spotAnimId, File outDir, OrbitView view) throws IOException
	{
		outDir.mkdirs();
		Playback playback = cache.open(spotAnimId);
		int frames = Math.max(1, playback.getFrameCount());
		int shown = Math.min(frames, 8);
		BufferedImage strip = new BufferedImage(shown * DUMP_TILE, DUMP_TILE, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = strip.createGraphics();
		g.setColor(new Color(40, 40, 40));
		g.fillRect(0, 0, strip.getWidth(), strip.getHeight());
		for (int i = 0; i < shown; i++)
		{
			int frame = i * frames / shown;
			BufferedImage tile = new BufferedImage(DUMP_TILE, DUMP_TILE, BufferedImage.TYPE_INT_ARGB);
			cache.renderer.drawOrbit(playback.posed(frame), view, DUMP_SUPERSAMPLE, tile);
			g.drawImage(tile, i * DUMP_TILE, 0, null);
			g.setColor(Color.WHITE);
			g.drawString("frame " + frame, i * DUMP_TILE + 4, DUMP_TILE - 6);
		}
		g.dispose();
		File out = new File(outDir, "spotanim_" + spotAnimId + ".png");
		ImageIO.write(strip, "png", out);
		System.out.println("wrote " + out.getAbsolutePath() + " (" + frames + " frames)");
	}

	/// The loaded cache, the renderer fed from it, and lazily loaded skeleton frames.
	static final class Cache
	{
		private final Store store;
		final List<SpotAnimDefinition> spotAnims = new ArrayList<>();
		final TextureProvider textures;
		final ModelRenderer renderer;
		private final Map<Integer, SequenceDefinition> sequences = new HashMap<>();
		private final Map<Integer, FramemapDefinition> framemaps = new HashMap<>();
		private final Map<Integer, FrameDefinition> frames = new HashMap<>();

		Cache(File cacheDir) throws IOException
		{
			store = new Store(cacheDir);
			store.load();
			SpriteManager sprites = new SpriteManager(store);
			sprites.load();
			TextureManager textureManager = new TextureManager(store);
			textureManager.load();
			textures = new CacheTextureProvider(textureManager, sprites);
			renderer = new ModelRenderer(textures);
			for (FSFile f : configFiles(ConfigType.SPOTANIM))
			{
				spotAnims.add(new SpotAnimLoader().load(f.getFileId(), f.getContents()));
			}
			spotAnims.sort(Comparator.comparingInt((SpotAnimDefinition s) -> s.id).reversed());
			for (FSFile f : configFiles(ConfigType.SEQUENCE))
			{
				sequences.put(f.getFileId(), new SequenceLoader().load(f.getFileId(), f.getContents()));
			}
		}

		Playback open(int spotAnimId) throws IOException
		{
			SpotAnimDefinition spot = null;
			for (SpotAnimDefinition s : spotAnims)
			{
				if (s.id == spotAnimId)
				{
					spot = s;
				}
			}
			if (spot == null)
			{
				throw new IllegalArgumentException("no spotanim " + spotAnimId);
			}
			ModelDefinition def = loadModel(spot.modelId);
			if (def == null)
			{
				throw new IllegalArgumentException("spotanim " + spotAnimId + " has no model " + spot.modelId);
			}
			if (spot.recolorToFind != null)
			{
				for (int i = 0; i < spot.recolorToFind.length; i++)
				{
					def.recolor(spot.recolorToFind[i], spot.recolorToReplace[i]);
				}
			}
			if (spot.textureToFind != null)
			{
				for (int i = 0; i < spot.textureToFind.length; i++)
				{
					def.retexture(spot.textureToFind[i], spot.textureToReplace[i]);
				}
			}
			if (spot.rotaton == 1)
			{
				def.rotate1();
			}
			else if (spot.rotaton == 2)
			{
				def.rotate2();
			}
			else if (spot.rotaton == 3)
			{
				def.rotate3();
			}
			if (spot.resizeX != 128 || spot.resizeY != 128)
			{
				def.resize(spot.resizeX, spot.resizeY, spot.resizeX);
			}
			def.computeAnimationTables();
			def.computeTextureUVCoordinates();
			CacheModelSnapshots.LitColors lit = CacheModelSnapshots.lightAsActor(def, spot.ambient, spot.contrast);

			SequenceDefinition sequence = spot.animationId == -1 ? null : sequences.get(spot.animationId);
			List<FrameDefinition> sequenceFrames = new ArrayList<>();
			int[] lengths = new int[0];
			if (sequence != null && sequence.frameIDs != null)
			{
				for (int frameId : sequence.frameIDs)
				{
					sequenceFrames.add(loadFrame(frameId));
				}
				lengths = sequence.frameLengths != null ? sequence.frameLengths : new int[sequence.frameIDs.length];
			}
			return new Playback(this, spot, def, lit, sequenceFrames, lengths, framingCorners(def, sequenceFrames));
		}

		/// Opposite corners of the model's bounds across every frame, as {xs, ys, zs}.
		private static float[][] framingCorners(ModelDefinition def, List<FrameDefinition> frames)
		{
			int[] bounds = {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE};
			includeBounds(def, bounds);
			for (FrameDefinition frame : frames)
			{
				if (frame != null)
				{
					def.resetAnim();
					applyFrame(def, frame);
					includeBounds(def, bounds);
				}
			}
			def.resetAnim();
			return new float[][]{
				{bounds[0], bounds[1]},
				{bounds[2], bounds[3]},
				{bounds[4], bounds[5]}};
		}

		private static void includeBounds(ModelDefinition def, int[] bounds)
		{
			for (int i = 0; i < def.vertexCount; i++)
			{
				bounds[0] = Math.min(bounds[0], def.vertexX[i]);
				bounds[1] = Math.max(bounds[1], def.vertexX[i]);
				bounds[2] = Math.min(bounds[2], def.vertexY[i]);
				bounds[3] = Math.max(bounds[3], def.vertexY[i]);
				bounds[4] = Math.min(bounds[4], def.vertexZ[i]);
				bounds[5] = Math.max(bounds[5], def.vertexZ[i]);
			}
		}

		static void applyFrame(ModelDefinition def, FrameDefinition frame)
		{
			// A model without skeleton labels cannot be animated; the client draws it static as well.
			if (def.getVertexGroups() == null)
			{
				return;
			}
			FramemapDefinition framemap = frame.framemap;
			for (int i = 0; i < frame.translatorCount; i++)
			{
				int group = frame.indexFrameIds[i];
				def.animate(framemap.types[group], framemap.frameMaps[group], frame.translator_x[i], frame.translator_y[i], frame.translator_z[i]);
			}
		}

		private ModelDefinition loadModel(int modelId) throws IOException
		{
			Archive archive = store.getIndex(IndexType.MODELS).getArchive(modelId);
			if (archive == null)
			{
				return null;
			}
			return new ModelLoader().load(modelId, archive.decompress(store.getStorage().loadArchive(archive)));
		}

		/// Sequence frame ids pack the animation archive in the high half and the file in the low half;
		/// each frame file names its skeleton (framemap) archive in its first two bytes.
		private FrameDefinition loadFrame(int frameId) throws IOException
		{
			FrameDefinition cached = frames.get(frameId);
			if (cached != null || frames.containsKey(frameId))
			{
				return cached;
			}
			FrameDefinition frame = null;
			Archive archive = store.getIndex(IndexType.ANIMATIONS).getArchive(frameId >> 16);
			if (archive != null)
			{
				ArchiveFiles files = archive.getFiles(store.getStorage().loadArchive(archive));
				FSFile file = files.findFile(frameId & 0xffff);
				if (file != null)
				{
					byte[] contents = file.getContents();
					int framemapId = (contents[0] & 0xff) << 8 | contents[1] & 0xff;
					FramemapDefinition framemap = loadFramemap(framemapId);
					if (framemap != null)
					{
						frame = new FrameLoader().load(framemap, file.getFileId(), contents);
					}
				}
			}
			frames.put(frameId, frame);
			return frame;
		}

		private FramemapDefinition loadFramemap(int framemapId) throws IOException
		{
			FramemapDefinition cached = framemaps.get(framemapId);
			if (cached != null)
			{
				return cached;
			}
			Archive archive = store.getIndex(IndexType.SKELETONS).getArchive(framemapId);
			if (archive == null)
			{
				return null;
			}
			ArchiveFiles files = archive.getFiles(store.getStorage().loadArchive(archive));
			FSFile file = files.getFiles().iterator().next();
			FramemapDefinition framemap = new FramemapLoader().load(framemapId, file.getContents());
			framemaps.put(framemapId, framemap);
			return framemap;
		}

		private Iterable<FSFile> configFiles(ConfigType type) throws IOException
		{
			Index configs = store.getIndex(IndexType.CONFIGS);
			Archive archive = configs.getArchive(type.getId());
			return archive.getFiles(store.getStorage().loadArchive(archive)).getFiles();
		}
	}

	/// One spotanim's lit model and skeleton frames, snapshotted at any frame of its sequence.
	static final class Playback
	{
		private final Cache cache;
		final SpotAnimDefinition spot;
		private final ModelDefinition def;
		private final CacheModelSnapshots.LitColors lit;
		private final List<FrameDefinition> frames;
		private final int[] frameLengths;
		private final float[][] framing;

		Playback(Cache cache, SpotAnimDefinition spot, ModelDefinition def, CacheModelSnapshots.LitColors lit,
				 List<FrameDefinition> frames, int[] frameLengths, float[][] framing)
		{
			this.cache = cache;
			this.spot = spot;
			this.def = def;
			this.lit = lit;
			this.frames = frames;
			this.frameLengths = frameLengths;
			this.framing = framing;
		}

		int getFrameCount()
		{
			return frames.size();
		}

		int getFrameDurationMs(int frame)
		{
			return Math.max(1, frame < frameLengths.length ? frameLengths[frame] : 1) * CLIENT_TICK_MS;
		}

		PosedModel posed(int frame)
		{
			def.resetAnim();
			FrameDefinition f = frame < frames.size() ? frames.get(frame) : null;
			if (f != null)
			{
				Cache.applyFrame(def, f);
			}
			ModelSnapshot snapshot = CacheModelSnapshots.capture(def, lit, cache.textures, Brightness.getCurrent(),
				framing[0], framing[1], framing[2]);
			return new PosedModel(snapshot, ModelPose.UPRIGHT);
		}
	}

	static final class PreviewWindow extends JFrame
	{
		private final Cache cache;
		private final DefaultListModel<SpotAnimDefinition> listModel = new DefaultListModel<>();
		private final JList<SpotAnimDefinition> list = new JList<>(listModel);
		private final JTextField filter = new JTextField();
		private final JLabel status = new JLabel(" ");
		private final JSlider frameSlider = new JSlider(0, 0, 0);
		private final JToggleButton play = new JToggleButton("Pause");
		private final Timer timer = new Timer(CLIENT_TICK_MS, e -> advanceFrame());
		private final ModelViewPanel view;
		private Playback playback;
		private int frame;

		PreviewWindow(Cache cache)
		{
			super("Spotanim previewer");
			this.cache = cache;
			setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			setLayout(new BorderLayout());

			list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			list.setCellRenderer((l, s, index, selected, focus) -> {
				JLabel label = new JLabel(describe(s));
				label.setOpaque(true);
				label.setBackground(selected ? l.getSelectionBackground() : l.getBackground());
				label.setForeground(selected ? l.getSelectionForeground() : l.getForeground());
				return label;
			});
			list.addListSelectionListener(e -> {
				if (!e.getValueIsAdjusting() && list.getSelectedValue() != null)
				{
					open(list.getSelectedValue().id);
				}
			});
			filter.getDocument().addDocumentListener(new DocumentListener()
			{
				public void insertUpdate(DocumentEvent e) { refill(); }
				public void removeUpdate(DocumentEvent e) { refill(); }
				public void changedUpdate(DocumentEvent e) { refill(); }
			});
			refill();

			JPanel side = new JPanel(new BorderLayout());
			side.add(filter, BorderLayout.NORTH);
			JScrollPane scroll = new JScrollPane(list);
			scroll.setPreferredSize(new Dimension(260, CANVAS));
			side.add(scroll, BorderLayout.CENTER);
			add(side, BorderLayout.WEST);

			Executor renderExecutor = Executors.newSingleThreadExecutor(runnable -> {
				Thread thread = new Thread(runnable, "spotanim-render");
				thread.setDaemon(true);
				return thread;
			});
			view = new ModelViewPanel(renderExecutor, cache.renderer);
			view.setPreferredSize(new Dimension(CANVAS, CANVAS));
			add(view, BorderLayout.CENTER);

			JPanel controls = new JPanel();
			controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
			controls.add(labelled("Frame", frameSlider));
			controls.add(play);
			controls.add(status);
			add(controls, BorderLayout.SOUTH);

			frameSlider.addChangeListener(e -> {
				if (frameSlider.getValueIsAdjusting() || playback == null)
				{
					return;
				}
				frame = frameSlider.getValue();
				showFrame();
			});
			play.setSelected(true);
			play.addActionListener(e -> {
				play.setText(play.isSelected() ? "Pause" : "Play");
				if (play.isSelected())
				{
					timer.restart();
				}
				else
				{
					timer.stop();
				}
			});

			pack();
			setLocationRelativeTo(null);
			if (!listModel.isEmpty())
			{
				list.setSelectedIndex(0);
			}
		}

		private JPanel labelled(String name, JSlider slider)
		{
			JPanel row = new JPanel(new BorderLayout());
			JLabel label = new JLabel(name);
			label.setPreferredSize(new Dimension(50, 20));
			row.add(label, BorderLayout.WEST);
			row.add(slider, BorderLayout.CENTER);
			return row;
		}

		private static String describe(SpotAnimDefinition s)
		{
			return s.id + "   model " + s.modelId + "   anim " + s.animationId;
		}

		private void refill()
		{
			String needle = filter.getText().trim();
			listModel.clear();
			for (SpotAnimDefinition s : cache.spotAnims)
			{
				if (needle.isEmpty() || describe(s).contains(needle))
				{
					listModel.addElement(s);
				}
			}
		}

		private void open(int spotAnimId)
		{
			timer.stop();
			try
			{
				playback = cache.open(spotAnimId);
			}
			catch (IOException | RuntimeException ex)
			{
				playback = null;
				view.setModel(null, "spotanim " + spotAnimId + ": " + ex.getMessage());
				status.setText("spotanim " + spotAnimId + ": " + ex.getMessage());
				return;
			}
			frame = 0;
			frameSlider.setMaximum(Math.max(0, playback.getFrameCount() - 1));
			frameSlider.setValue(0);
			showFrame();
			if (play.isSelected() && playback.getFrameCount() > 1)
			{
				timer.setInitialDelay(playback.getFrameDurationMs(0));
				timer.restart();
			}
		}

		private void advanceFrame()
		{
			if (playback == null || playback.getFrameCount() == 0)
			{
				return;
			}
			frame = (frame + 1) % playback.getFrameCount();
			frameSlider.setValue(frame);
			showFrame();
			timer.setInitialDelay(playback.getFrameDurationMs(frame));
			timer.restart();
		}

		private void showFrame()
		{
			if (playback == null)
			{
				return;
			}
			view.setModel(playback.posed(frame), "");
			SpotAnimDefinition s = playback.spot;
			status.setText("spotanim " + s.id + "  model " + s.modelId + "  anim " + s.animationId
				+ "  frame " + frame + "/" + playback.getFrameCount()
				+ "  ambient " + s.ambient + "  contrast " + s.contrast);
		}
	}
}
