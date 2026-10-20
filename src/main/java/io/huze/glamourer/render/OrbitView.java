package io.huze.glamourer.render;

import javax.annotation.Nonnull;
import lombok.Getter;

public final class OrbitView
{
	public static final OrbitView INITIAL = new OrbitView(ViewTransform.IDENTITY, 0, 0, 1, 0, 0);

	@Nonnull
	final ViewTransform orientation;
	@Getter
	private final double turn;
	@Getter
	private final double tilt;
	@Getter
	private final double zoom;
	@Getter
	private final double panX;
	@Getter
	private final double panY;

	private OrbitView(@Nonnull ViewTransform orientation, double turn, double tilt, double zoom, double panX, double panY)
	{
		this.orientation = orientation;
		this.turn = turn;
		this.tilt = tilt;
		this.zoom = zoom;
		this.panX = panX;
		this.panY = panY;
	}

	@Nonnull
	public OrbitView turned(double byTurn, double byTilt)
	{
		return new OrbitView(orientation, turn + byTurn, tilt + byTilt, zoom, panX, panY);
	}

	@Nonnull
	public OrbitView rotatedAboutPosedAxis(double axisX, double axisY, double axisZ, double angle)
	{
		var rotation = ViewTransform.rotation(axisX, axisY, axisZ, angle);
		return new OrbitView(rotation.then(orientation), turn, tilt, zoom, panX, panY);
	}

	@Nonnull
	public OrbitView rotatedAboutViewAxis(double angle)
	{
		var turntableInverse = ViewTransform.tilt(-Math.sin(tilt), Math.cos(tilt))
			.then(ViewTransform.turn(-Math.sin(turn), Math.cos(turn)));
		var rotation = ViewTransform.rotation(
			turntableInverse.toCameraX(0, 0, 1), turntableInverse.toCameraY(0, 0, 1), turntableInverse.toDepth(0, 0, 1),
			angle);
		return new OrbitView(orientation.then(rotation), turn, tilt, zoom, panX, panY);
	}

	@Nonnull
	public OrbitView panned(double byX, double byY)
	{
		return new OrbitView(orientation, turn, tilt, zoom, panX + byX, panY + byY);
	}

	@Nonnull
	public OrbitView zoomedTo(double zoom)
	{
		return new OrbitView(orientation, turn, tilt, zoom, panX, panY);
	}
}
