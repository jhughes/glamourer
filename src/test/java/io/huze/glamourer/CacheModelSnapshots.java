package io.huze.glamourer;

import io.huze.glamourer.color.Brightness;
import io.huze.glamourer.color.Shade;
import io.huze.glamourer.render.ModelSnapshot;
import io.huze.glamourer.texture.SamplableTexture;
import io.huze.glamourer.texture.TextureProvider;
import java.util.Arrays;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.models.FaceNormal;
import net.runelite.cache.models.VertexNormal;

/// Lights a cache model the way the client lights an actor and snapshots it for the plugin renderer.
/// The lighting follows the client's Model.light: textured faces get a shade only, the third colour
/// carries the flat-shaded (-1) and invisible (-2) markers, and hue and saturation pass through.
final class CacheModelSnapshots
{
	private static final int FLAT_SHADED_FACE = -1;
	private static final int INVISIBLE_FACE = -2;
	private static final int FULLY_TRANSPARENT = 255;
	private static final int ACTOR_LIGHT_X = -30;
	private static final int ACTOR_LIGHT_Y = -50;
	private static final int ACTOR_LIGHT_Z = -30;
	private static final int ACTOR_CONTRAST = 850;

	/// Lit colours per face corner, in the client's packed HSL with the client's markers.
	static final class LitColors
	{
		final int[] corner1;
		final int[] corner2;
		final int[] corner3;

		LitColors(int faceCount)
		{
			corner1 = new int[faceCount];
			corner2 = new int[faceCount];
			corner3 = new int[faceCount];
		}
	}

	/// Lights from the rest pose; the client keeps these colours while the vertices animate.
	@Nonnull
	static LitColors lightAsActor(@Nonnull ModelDefinition def, int ambientOffset, int contrastOffset)
	{
		def.computeNormals();
		int ambient = Shade.AMBIENT + ambientOffset;
		int contrast = ACTOR_CONTRAST + contrastOffset;
		int x = ACTOR_LIGHT_X;
		int y = ACTOR_LIGHT_Y;
		int z = ACTOR_LIGHT_Z;
		int magnitude = (int) Math.sqrt((double) (z * z + x * x + y * y));
		int attenuation = magnitude * contrast >> 8;
		LitColors lit = new LitColors(def.faceCount);
		for (int face = 0; face < def.faceCount; face++)
		{
			byte faceType = def.faceRenderTypes == null ? 0 : def.faceRenderTypes[face];
			byte faceAlpha = def.faceTransparencies == null ? 0 : def.faceTransparencies[face];
			boolean textured = def.faceTextures != null && def.faceTextures[face] != -1;
			if (faceAlpha == -2)
			{
				faceType = 3;
			}
			if (faceAlpha == -1)
			{
				faceType = 2;
			}
			int hsl = def.faceColors[face] & 0xffff;
			if (faceType == 1)
			{
				FaceNormal normal = def.faceNormals[face];
				int shade = (y * normal.y + z * normal.z + x * normal.x) / (attenuation / 2 + attenuation) + ambient;
				lit.corner1[face] = textured ? boundShade(shade) : shadeHsl(hsl, shade);
				lit.corner3[face] = FLAT_SHADED_FACE;
			}
			else if (faceType == 3 && !textured)
			{
				lit.corner1[face] = Shade.FULL;
				lit.corner3[face] = FLAT_SHADED_FACE;
			}
			else if (faceType != 0)
			{
				lit.corner3[face] = INVISIBLE_FACE;
			}
			else
			{
				lit.corner1[face] = shadeCorner(def.vertexNormals[def.faceIndices1[face]], hsl, textured, attenuation, ambient);
				lit.corner2[face] = shadeCorner(def.vertexNormals[def.faceIndices2[face]], hsl, textured, attenuation, ambient);
				lit.corner3[face] = shadeCorner(def.vertexNormals[def.faceIndices3[face]], hsl, textured, attenuation, ambient);
			}
		}
		return lit;
	}

	private static int shadeCorner(VertexNormal normal, int hsl, boolean textured, int attenuation, int ambient)
	{
		int shade = (ACTOR_LIGHT_Y * normal.y + ACTOR_LIGHT_Z * normal.z + ACTOR_LIGHT_X * normal.x)
			/ (attenuation * normal.magnitude) + ambient;
		return textured ? boundShade(shade) : shadeHsl(hsl, shade);
	}

	private static int shadeHsl(int hsl, int shade)
	{
		int luminance = boundShade((hsl & 127) * shade >> 7);
		return (hsl & 65408) + luminance;
	}

	private static int boundShade(int shade)
	{
		return Math.max(Shade.DARKEST, Math.min(Shade.BRIGHTEST, shade));
	}

	/// Mirrors ModelReader.captureSnapshot for a cache model whose vertices may be mid-animation.
	/// The framing corners are extra unreferenced vertices that widen the snapshot's bounds, so a
	/// graphic that flies away from its origin keeps the same camera for every frame.
	@Nonnull
	static ModelSnapshot capture(@Nonnull ModelDefinition def, @Nonnull LitColors lit, @Nonnull TextureProvider textures,
								 @Nonnull Brightness brightness, @Nonnull float[] framingX, @Nonnull float[] framingY, @Nonnull float[] framingZ)
	{
		int faceCount = def.faceCount;
		int[] indices1 = new int[faceCount];
		int[] indices2 = new int[faceCount];
		int[] indices3 = new int[faceCount];
		int[] colors1 = new int[faceCount];
		int[] colors2 = new int[faceCount];
		int[] colors3 = new int[faceCount];
		byte[] transparencies = def.faceTransparencies == null ? null : new byte[faceCount];
		short[] textureIds = def.faceTextures == null ? null : new short[faceCount];
		float[][] uv = def.faceTextures == null ? null : new float[6][faceCount];
		byte[] priorities = def.faceRenderPriorities == null ? null : new byte[faceCount];
		boolean animated = false;

		int drawn = 0;
		for (int face = 0; face < faceCount; face++)
		{
			if (lit.corner3[face] == INVISIBLE_FACE
				|| (def.faceTransparencies != null && (def.faceTransparencies[face] & 0xFF) >= FULLY_TRANSPARENT))
			{
				continue;
			}
			boolean flatShaded = lit.corner3[face] == FLAT_SHADED_FACE;
			int color1 = lit.corner1[face];
			int color2 = flatShaded ? color1 : lit.corner2[face];
			int color3 = flatShaded ? color1 : lit.corner3[face];

			short textureId = def.faceTextures == null ? ModelSnapshot.UNTEXTURED : def.faceTextures[face];
			SamplableTexture texture = textureId == ModelSnapshot.UNTEXTURED ? null : textures.get(textureId);
			float[] u = texture != null && texture.isUsable() && def.faceTextureUCoordinates != null ? def.faceTextureUCoordinates[face] : null;
			float[] v = u == null || def.faceTextureVCoordinates == null ? null : def.faceTextureVCoordinates[face];
			if (u != null && v != null)
			{
				textureIds[drawn] = textureId;
				for (int corner = 0; corner < 3; corner++)
				{
					uv[corner][drawn] = u[corner];
					uv[3 + corner][drawn] = v[corner];
				}
				animated |= texture.getAnimation().isAnimated();
			}
			else if (texture != null)
			{
				textureIds[drawn] = ModelSnapshot.UNTEXTURED;
				color1 = brightness.toRgb(Shade.shadeHsl(texture.getAverageColor(), color1));
				color2 = brightness.toRgb(Shade.shadeHsl(texture.getAverageColor(), color2));
				color3 = brightness.toRgb(Shade.shadeHsl(texture.getAverageColor(), color3));
			}
			else
			{
				if (textureIds != null)
				{
					textureIds[drawn] = ModelSnapshot.UNTEXTURED;
				}
				color1 = brightness.toRgb((short) color1);
				color2 = brightness.toRgb((short) color2);
				color3 = brightness.toRgb((short) color3);
			}

			indices1[drawn] = def.faceIndices1[face];
			indices2[drawn] = def.faceIndices2[face];
			indices3[drawn] = def.faceIndices3[face];
			colors1[drawn] = color1;
			colors2[drawn] = color2;
			colors3[drawn] = color3;
			if (transparencies != null)
			{
				transparencies[drawn] = def.faceTransparencies[face];
			}
			if (priorities != null)
			{
				priorities[drawn] = def.faceRenderPriorities[face];
			}
			drawn++;
		}

		return ModelSnapshot.builder()
			.verticesX(withFraming(def.vertexX, def.vertexCount, framingX))
			.verticesY(withFraming(def.vertexY, def.vertexCount, framingY))
			.verticesZ(withFraming(def.vertexZ, def.vertexCount, framingZ))
			.faceIndices1(Arrays.copyOf(indices1, drawn))
			.faceIndices2(Arrays.copyOf(indices2, drawn))
			.faceIndices3(Arrays.copyOf(indices3, drawn))
			.faceColors1(Arrays.copyOf(colors1, drawn))
			.faceColors2(Arrays.copyOf(colors2, drawn))
			.faceColors3(Arrays.copyOf(colors3, drawn))
			.faceTransparencies(trim(transparencies, drawn))
			.faceTextures(trim(textureIds, drawn))
			.faceU1(trim(uv, 0, drawn))
			.faceU2(trim(uv, 1, drawn))
			.faceU3(trim(uv, 2, drawn))
			.faceV1(trim(uv, 3, drawn))
			.faceV2(trim(uv, 4, drawn))
			.faceV3(trim(uv, 5, drawn))
			.faceRenderPriorities(trim(priorities, drawn))
			.animated(animated)
			.build();
	}

	@Nonnull
	private static float[] withFraming(@Nonnull int[] vertices, int vertexCount, @Nonnull float[] framing)
	{
		float[] result = new float[vertexCount + framing.length];
		for (int i = 0; i < vertexCount; i++)
		{
			result[i] = vertices[i];
		}
		System.arraycopy(framing, 0, result, vertexCount, framing.length);
		return result;
	}

	@Nullable
	private static short[] trim(@Nullable short[] array, int count)
	{
		return array == null ? null : Arrays.copyOf(array, count);
	}

	@Nullable
	private static byte[] trim(@Nullable byte[] array, int count)
	{
		return array == null ? null : Arrays.copyOf(array, count);
	}

	@Nullable
	private static float[] trim(@Nullable float[][] arrays, int which, int count)
	{
		return arrays == null ? null : Arrays.copyOf(arrays[which], count);
	}
}
