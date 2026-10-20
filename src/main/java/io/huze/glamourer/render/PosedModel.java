package io.huze.glamourer.render;

import javax.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public final class PosedModel
{
	@Getter
	@Nonnull
	private final ModelSnapshot snapshot;
	@Nonnull
	final ModelPose pose;
}
