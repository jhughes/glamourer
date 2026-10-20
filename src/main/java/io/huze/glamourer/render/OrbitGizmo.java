package io.huze.glamourer.render;

import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;

public final class OrbitGizmo
{
	public enum Ring
	{
		X,
		Y,
		Z,
		VIEW
	}

	private static final int POINTS_PER_RING = 64;
	/// Of the viewport's shorter side.
	private static final double RING_FRACTION = 0.3;
	private static final double VIEW_RING_EXTRA_PIXELS = 14;
	private static final double HIT_TOLERANCE_PIXELS = 7;
	private static final double TRIAL_ANGLE = 0.01;
	/// A ring squashed narrower than this is dragged along its line instead of round.
	private static final double EDGE_ON_RATIO = 0.15;

	public static final class RingShape
	{
		@Getter
		@Nonnull
		private final Ring ring;
		private final double[] xs;
		private final double[] ys;
		private final double[] depths;
		private final boolean[] behind;
		@Getter
		private final boolean edgeOn;
		private final double lineX;
		private final double lineY;

		private RingShape(@Nonnull Ring ring, double[] xs, double[] ys, double[] depths, double centreX, double centreY,
						  double centreDepth)
		{
			this.ring = ring;
			this.xs = xs;
			this.ys = ys;
			this.depths = depths;
			this.behind = new boolean[xs.length];
			double nearest = Double.MAX_VALUE;
			double furthest = 0;
			int furthestPoint = 0;
			for (int point = 0; point < xs.length; point++)
			{
				behind[point] = depths[point] > centreDepth;
				double radius = Math.hypot(xs[point] - centreX, ys[point] - centreY);
				nearest = Math.min(nearest, radius);
				if (radius > furthest)
				{
					furthest = radius;
					furthestPoint = point;
				}
			}
			this.edgeOn = nearest < furthest * EDGE_ON_RATIO;
			this.lineX = (xs[furthestPoint] - centreX) / furthest;
			this.lineY = (ys[furthestPoint] - centreY) / furthest;
		}

		private int getNearestToCamera()
		{
			int nearest = 0;
			for (int point = 1; point < depths.length; point++)
			{
				if (depths[point] < depths[nearest])
				{
					nearest = point;
				}
			}
			return nearest;
		}

		public int getPointCount()
		{
			return xs.length;
		}

		public double getX(int point)
		{
			return xs[point];
		}

		public double getY(int point)
		{
			return ys[point];
		}

		public boolean isBehind(int point)
		{
			return behind[point];
		}
	}

	@Nonnull
	private final PosedModel model;
	@Nonnull
	private final OrbitView view;
	private final int width;
	private final int height;
	@Nonnull
	private final OrbitCamera camera;
	private final double centreX;
	private final double centreY;
	private final double itemRadiusPixels;
	@Getter
	@Nonnull
	private final List<RingShape> rings;

	@Nonnull
	public static OrbitGizmo of(@Nonnull PosedModel model, @Nonnull OrbitView view, int width, int height)
	{
		return new OrbitGizmo(model, view, width, height);
	}

	private OrbitGizmo(@Nonnull PosedModel model, @Nonnull OrbitView view, int width, int height)
	{
		this.model = model;
		this.view = view;
		this.width = width;
		this.height = height;

		this.camera = new OrbitCamera(model.getSnapshot().getBounds(), model.pose, view, width, height);
		double[] centre = camera.projectModelOffset(0, 0, 0);
		this.centreX = centre[0];
		this.centreY = centre[1];
		this.itemRadiusPixels = RING_FRACTION * Math.min(width, height);
		double itemRadius = itemRadiusPixels * camera.getModelUnitsPerPixelAtCentre();
		this.rings = List.of(
			itemRing(Ring.X, itemRadius, centre[2]),
			itemRing(Ring.Y, itemRadius, centre[2]),
			itemRing(Ring.Z, itemRadius, centre[2]),
			viewRing(itemRadiusPixels + VIEW_RING_EXTRA_PIXELS));
	}

	@Nonnull
	private RingShape itemRing(@Nonnull Ring ring, double radius, double centreDepth)
	{
		double[] xs = new double[POINTS_PER_RING];
		double[] ys = new double[POINTS_PER_RING];
		double[] depths = new double[POINTS_PER_RING];
		for (int point = 0; point < POINTS_PER_RING; point++)
		{
			double along = radius * Math.cos(2 * Math.PI * point / POINTS_PER_RING);
			double across = radius * Math.sin(2 * Math.PI * point / POINTS_PER_RING);
			double[] projected;
			switch (ring)
			{
				case X:
					projected = camera.projectModelOffset(0, along, across);
					break;
				case Y:
					projected = camera.projectModelOffset(along, 0, across);
					break;
				default:
					projected = camera.projectModelOffset(along, across, 0);
					break;
			}
			xs[point] = projected[0];
			ys[point] = projected[1];
			depths[point] = projected[2];
		}
		return new RingShape(ring, xs, ys, depths, centreX, centreY, centreDepth);
	}

	@Nonnull
	private RingShape viewRing(double radius)
	{
		double[] xs = new double[POINTS_PER_RING];
		double[] ys = new double[POINTS_PER_RING];
		for (int point = 0; point < POINTS_PER_RING; point++)
		{
			xs[point] = centreX + radius * Math.cos(2 * Math.PI * point / POINTS_PER_RING);
			ys[point] = centreY + radius * Math.sin(2 * Math.PI * point / POINTS_PER_RING);
		}
		return new RingShape(Ring.VIEW, xs, ys, new double[POINTS_PER_RING], centreX, centreY, 0);
	}

	@Nullable
	public Ring hitTest(double x, double y)
	{
		Ring nearest = null;
		double nearestDistance = HIT_TOLERANCE_PIXELS;
		for (RingShape shape : rings)
		{
			double distance = distanceToRing(shape, x, y);
			if (distance < nearestDistance)
			{
				nearest = shape.ring;
				nearestDistance = distance;
			}
		}
		return nearest;
	}

	@Nonnull
	public Drag startDrag(@Nonnull Ring ring, double x, double y)
	{
		return new Drag(ring, x, y);
	}

	/// The cursor's angle about the centre becomes the turn. An edge on ring is pulled along its
	/// line instead, one radius per radian.
	public final class Drag
	{
		@Getter
		@Nonnull
		private final Ring ring;
		@Nonnull
		private final RingShape shape;
		/// Screen direction of a positive turn.
		private final double sign;
		private double lastX;
		private double lastY;

		private Drag(@Nonnull Ring ring, double x, double y)
		{
			this.ring = ring;
			this.shape = rings.get(ring.ordinal());
			this.lastX = x;
			this.lastY = y;
			this.sign = shape.edgeOn ? getRimSign(ring) : getScreenSign(ring, x, y);
		}

		@Nonnull
		public OrbitView dragTo(@Nonnull OrbitView current, double x, double y)
		{
			double turned = shape.edgeOn
				? ((x - lastX) * shape.lineX + (y - lastY) * shape.lineY) / itemRadiusPixels
				: normalize(angleAround(x, y) - angleAround(lastX, lastY));
			lastX = x;
			lastY = y;
			return rotate(current, ring, sign * turned);
		}
	}

	@Nonnull
	private OrbitView rotate(@Nonnull OrbitView view, @Nonnull Ring ring, double angle)
	{
		if (ring == Ring.VIEW)
		{
			return view.rotatedAboutViewAxis(angle);
		}
		double[] axis = camera.toPosedOffset(ring == Ring.X ? 1 : 0, ring == Ring.Y ? 1 : 0, ring == Ring.Z ? 1 : 0);
		return view.rotatedAboutPosedAxis(axis[0], axis[1], axis[2], angle);
	}

	private double getScreenSign(@Nonnull Ring ring, double x, double y)
	{
		Ring probe = ring == Ring.VIEW ? getOpenItemRing() : ring;
		RingShape shape = rings.get(probe.ordinal());
		int grabbed = nearestPoint(shape, x, y);
		double before = angleAround(shape.xs[grabbed], shape.ys[grabbed]);
		var nudged = new OrbitGizmo(model, rotate(view, ring, TRIAL_ANGLE), width, height);
		RingShape nudgedShape = nudged.rings.get(probe.ordinal());
		double after = nudged.angleAround(nudgedShape.xs[grabbed], nudgedShape.ys[grabbed]);
		return normalize(after - before) >= 0 ? 1 : -1;
	}

	private double getRimSign(@Nonnull Ring ring)
	{
		RingShape shape = rings.get(ring.ordinal());
		int near = shape.getNearestToCamera();
		var nudged = new OrbitGizmo(model, rotate(view, ring, TRIAL_ANGLE), width, height);
		RingShape nudgedShape = nudged.rings.get(ring.ordinal());
		double along = (nudgedShape.xs[near] - shape.xs[near]) * shape.lineX
			+ (nudgedShape.ys[near] - shape.ys[near]) * shape.lineY;
		return along >= 0 ? 1 : -1;
	}

	@Nonnull
	private Ring getOpenItemRing()
	{
		for (RingShape shape : rings)
		{
			if (shape.ring != Ring.VIEW && !shape.edgeOn)
			{
				return shape.ring;
			}
		}
		return Ring.Z;
	}

	private double angleAround(double x, double y)
	{
		return Math.atan2(y - centreY, x - centreX);
	}

	private static double normalize(double angle)
	{
		return Math.atan2(Math.sin(angle), Math.cos(angle));
	}

	private static int nearestPoint(@Nonnull RingShape shape, double x, double y)
	{
		int nearest = 0;
		double nearestDistance = Double.MAX_VALUE;
		for (int point = 0; point < shape.xs.length; point++)
		{
			double distance = Math.hypot(shape.xs[point] - x, shape.ys[point] - y);
			if (distance < nearestDistance)
			{
				nearest = point;
				nearestDistance = distance;
			}
		}
		return nearest;
	}

	private static double distanceToRing(@Nonnull RingShape shape, double x, double y)
	{
		double nearest = Double.MAX_VALUE;
		int count = shape.xs.length;
		for (int point = 0; point < count; point++)
		{
			int next = (point + 1) % count;
			nearest = Math.min(nearest, distanceToSegment(
				shape.xs[point], shape.ys[point], shape.xs[next], shape.ys[next], x, y));
		}
		return nearest;
	}

	private static double distanceToSegment(double x1, double y1, double x2, double y2, double x, double y)
	{
		double dx = x2 - x1;
		double dy = y2 - y1;
		double length = dx * dx + dy * dy;
		double along = length == 0 ? 0 : Math.max(0, Math.min(1, ((x - x1) * dx + (y - y1) * dy) / length));
		return Math.hypot(x - (x1 + along * dx), y - (y1 + along * dy));
	}
}
