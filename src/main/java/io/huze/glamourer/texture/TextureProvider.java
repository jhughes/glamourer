package io.huze.glamourer.texture;

import javax.annotation.Nonnull;

public interface TextureProvider
{
	@Nonnull
	SamplableTexture get(short textureId);
}
