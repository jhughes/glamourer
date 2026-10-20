package io.huze.glamourer.model;

import io.huze.glamourer.color.Brightness;
import io.huze.glamourer.color.Shade;
import io.huze.glamourer.render.ModelSnapshot;
import io.huze.glamourer.texture.SamplableTextureProvider;
import java.util.Arrays;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.Model;

/// Snapshots a lit model into a [ModelSnapshot]. Client thread only.
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class ModelReader
{
	/// Lighting markers in a face's third color.
	private static final int FLAT_SHADED_FACE = -1;
	private static final int INVISIBLE_FACE = -2;
	private static final int FULLY_TRANSPARENT = 255;
	/// The texture maps through the face's own corners.
	private static final byte OWN_TEXTURE_TRIANGLE = -1;

	private final Client client;
	private final SamplableTextureProvider textureProvider;

	@Nonnull
	ModelSnapshot captureSnapshot(@Nonnull Model model, @Nullable FaceIndices indices)
	{
		textureProvider.loadIfStale();
		final var faceCount = model.getFaceCount();
		final var faceIndices1 = model.getFaceIndices1();
		final var faceIndices2 = model.getFaceIndices2();
		final var faceIndices3 = model.getFaceIndices3();
		final var faceColors1 = model.getFaceColors1();
		final var faceColors2 = model.getFaceColors2();
		final var faceColors3 = model.getFaceColors3();
		final var faceTransparencies = model.getFaceTransparencies();
		final var faceTextures = model.getFaceTextures();
		final var faceRenderPriorities = model.getFaceRenderPriorities();
		final var faceBias = model.getFaceBias();
		final var brightness = getBrightness();

		var drawnIndices1 = new int[faceCount];
		var drawnIndices2 = new int[faceCount];
		var drawnIndices3 = new int[faceCount];
		var drawnColors1 = new int[faceCount];
		var drawnColors2 = new int[faceCount];
		var drawnColors3 = new int[faceCount];
		var drawnTransparencies = faceTransparencies == null ? null : new byte[faceCount];
		var drawnTextures = faceTextures == null ? null : new short[faceCount];
		var drawnTextureCoordinates = faceTextures == null ? null : new float[6][faceCount];
		var drawnPriorities = faceRenderPriorities == null ? null : new byte[faceCount];
		var drawnBias = faceBias == null ? null : new byte[faceCount];
		var drawnColorIndices = indices == null ? null : new short[faceCount];
		var drawnTextureIndices = indices == null ? null : new short[faceCount];
		var animated = false;

		int drawn = 0;
		for (int face = 0; face < faceCount; face++)
		{
			if (faceColors3[face] == INVISIBLE_FACE
				|| (faceTransparencies != null && (faceTransparencies[face] & 0xFF) >= FULLY_TRANSPARENT))
			{
				continue;
			}
			var flatShaded = faceColors3[face] == FLAT_SHADED_FACE;
			var color1 = faceColors1[face];
			var color2 = flatShaded ? color1 : faceColors2[face];
			var color3 = flatShaded ? color1 : faceColors3[face];

			var textureId = faceTextures == null ? ModelSnapshot.UNTEXTURED : faceTextures[face];
			var texture = textureId == ModelSnapshot.UNTEXTURED ? null : textureProvider.get(textureId);
			var textureCoordinates = texture != null && texture.isUsable() ? getTextureCoordinates(model, face) : null;
			if (textureCoordinates != null)
			{
				drawnTextures[drawn] = textureId;
				for (int coordinate = 0; coordinate < 6; coordinate++)
				{
					drawnTextureCoordinates[coordinate][drawn] = textureCoordinates[coordinate];
				}
				animated |= texture.getAnimation().isAnimated();
			}
			else if (texture != null)
			{
				drawnTextures[drawn] = ModelSnapshot.UNTEXTURED;
				color1 = brightness.toRgb(Shade.shadeHsl(texture.getAverageColor(), color1));
				color2 = brightness.toRgb(Shade.shadeHsl(texture.getAverageColor(), color2));
				color3 = brightness.toRgb(Shade.shadeHsl(texture.getAverageColor(), color3));
			}
			else
			{
				if (drawnTextures != null)
				{
					drawnTextures[drawn] = ModelSnapshot.UNTEXTURED;
				}
				color1 = brightness.toRgb((short) color1);
				color2 = brightness.toRgb((short) color2);
				color3 = brightness.toRgb((short) color3);
			}

			drawnIndices1[drawn] = faceIndices1[face];
			drawnIndices2[drawn] = faceIndices2[face];
			drawnIndices3[drawn] = faceIndices3[face];
			drawnColors1[drawn] = color1;
			drawnColors2[drawn] = color2;
			drawnColors3[drawn] = color3;
			if (drawnTransparencies != null)
			{
				drawnTransparencies[drawn] = faceTransparencies[face];
			}
			if (drawnPriorities != null)
			{
				drawnPriorities[drawn] = faceRenderPriorities[face];
			}
			if (drawnBias != null)
			{
				drawnBias[drawn] = faceBias[face];
			}
			if (indices != null)
			{
				drawnColorIndices[drawn] = indices.colors[face];
				drawnTextureIndices[drawn] = indices.textures[face];
			}
			drawn++;
		}

		final int vertexCount = model.getVerticesCount();
		return ModelSnapshot.builder()
			.verticesX(Arrays.copyOf(model.getVerticesX(), vertexCount))
			.verticesY(Arrays.copyOf(model.getVerticesY(), vertexCount))
			.verticesZ(Arrays.copyOf(model.getVerticesZ(), vertexCount))
			.faceIndices1(Arrays.copyOf(drawnIndices1, drawn))
			.faceIndices2(Arrays.copyOf(drawnIndices2, drawn))
			.faceIndices3(Arrays.copyOf(drawnIndices3, drawn))
			.faceColors1(Arrays.copyOf(drawnColors1, drawn))
			.faceColors2(Arrays.copyOf(drawnColors2, drawn))
			.faceColors3(Arrays.copyOf(drawnColors3, drawn))
			.faceTransparencies(trim(drawnTransparencies, drawn))
			.faceTextures(trim(drawnTextures, drawn))
			.faceU1(trim(drawnTextureCoordinates, 0, drawn))
			.faceU2(trim(drawnTextureCoordinates, 1, drawn))
			.faceU3(trim(drawnTextureCoordinates, 2, drawn))
			.faceV1(trim(drawnTextureCoordinates, 3, drawn))
			.faceV2(trim(drawnTextureCoordinates, 4, drawn))
			.faceV3(trim(drawnTextureCoordinates, 5, drawn))
			.faceRenderPriorities(trim(drawnPriorities, drawn))
			.faceBias(trim(drawnBias, drawn))
			.faceColorIndices(trim(drawnColorIndices, drawn))
			.faceTextureIndices(trim(drawnTextureIndices, drawn))
			.animated(animated)
			.build();
	}

	/// Returns `[u1, u2, u3, v1, v2, v3]` or null when the triangle has no plane.
	@Nullable
	private static float[] getTextureCoordinates(@Nonnull Model model, int face)
	{
		var triangle = getTextureTriangle(model, face);
		var xs = model.getVerticesX();
		var ys = model.getVerticesY();
		var zs = model.getVerticesZ();
		var origin = triangle[0];
		var columnEdge = edge(xs, ys, zs, origin, triangle[1]);
		var rowEdge = edge(xs, ys, zs, origin, triangle[2]);
		var normal = cross(columnEdge, rowEdge);
		var columnAxis = cross(rowEdge, normal);
		var rowAxis = cross(columnEdge, normal);
		var columnScale = dot(columnAxis, columnEdge);
		var rowScale = dot(rowAxis, rowEdge);
		if (columnScale == 0 || rowScale == 0)
		{
			return null;
		}

		int[] corners = {model.getFaceIndices1()[face], model.getFaceIndices2()[face], model.getFaceIndices3()[face]};
		var coordinates = new float[6];
		for (int corner = 0; corner < 3; corner++)
		{
			var fromOrigin = edge(xs, ys, zs, origin, corners[corner]);
			coordinates[corner] = (float) (dot(columnAxis, fromOrigin) / columnScale);
			coordinates[3 + corner] = (float) (dot(rowAxis, fromOrigin) / rowScale);
		}
		return coordinates;
	}

	/// Returns `[origin, column end, row end]`.
	@Nonnull
	private static int[] getTextureTriangle(@Nonnull Model model, int face)
	{
		var textureFaces = model.getTextureFaces();
		var texIndices1 = model.getTexIndices1();
		if (textureFaces != null && textureFaces[face] != OWN_TEXTURE_TRIANGLE && texIndices1 != null)
		{
			int triangle = textureFaces[face] & 0xFF;
			if (triangle < texIndices1.length)
			{
				int[] shared = {texIndices1[triangle], model.getTexIndices2()[triangle], model.getTexIndices3()[triangle]};
				if (isWithin(shared, model.getVerticesCount()))
				{
					return shared;
				}
			}
		}
		return new int[]{model.getFaceIndices1()[face], model.getFaceIndices2()[face], model.getFaceIndices3()[face]};
	}

	private static boolean isWithin(@Nonnull int[] vertices, int vertexCount)
	{
		for (int vertex : vertices)
		{
			if (vertex < 0 || vertex >= vertexCount)
			{
				return false;
			}
		}
		return true;
	}

	@Nonnull
	private static double[] edge(float[] xs, float[] ys, float[] zs, int from, int to)
	{
		return new double[]{xs[to] - xs[from], ys[to] - ys[from], zs[to] - zs[from]};
	}

	@Nonnull
	private static double[] cross(@Nonnull double[] a, @Nonnull double[] b)
	{
		return new double[]{
			a[1] * b[2] - a[2] * b[1],
			a[2] * b[0] - a[0] * b[2],
			a[0] * b[1] - a[1] * b[0]};
	}

	private static double dot(@Nonnull double[] a, @Nonnull double[] b)
	{
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	@Nonnull
	private Brightness getBrightness()
	{
		var clientTextureProvider = client.getTextureProvider();
		return Brightness.of(clientTextureProvider == null
			? Brightness.DEFAULT
			: clientTextureProvider.getBrightness());
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
