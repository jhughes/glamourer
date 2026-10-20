package io.huze.glamourer.render;

/// Model space to camera space: x right, y down, z away.
final class ViewTransform
{
	static final ViewTransform IDENTITY = new ViewTransform(
		1, 0, 0, 0,
		0, 1, 0, 0,
		0, 0, 1, 0);

	private final double xx, xy, xz, xw;
	private final double yx, yy, yz, yw;
	private final double zx, zy, zz, zw;

	private ViewTransform(double xx, double xy, double xz, double xw,
						  double yx, double yy, double yz, double yw,
						  double zx, double zy, double zz, double zw)
	{
		this.xx = xx;
		this.xy = xy;
		this.xz = xz;
		this.xw = xw;
		this.yx = yx;
		this.yy = yy;
		this.yz = yz;
		this.yw = yw;
		this.zx = zx;
		this.zy = zy;
		this.zz = zz;
		this.zw = zw;
	}

	static ViewTransform translation(double x, double y, double z)
	{
		return new ViewTransform(
			1, 0, 0, x,
			0, 1, 0, y,
			0, 0, 1, z);
	}

	/// The client's zan2d.
	static ViewTransform roll(double sin, double cos)
	{
		return new ViewTransform(
			cos, sin, 0, 0,
			-sin, cos, 0, 0,
			0, 0, 1, 0);
	}

	/// The client's yan2d.
	static ViewTransform turn(double sin, double cos)
	{
		return new ViewTransform(
			cos, 0, sin, 0,
			0, 1, 0, 0,
			-sin, 0, cos, 0);
	}

	/// The client's xan2d.
	static ViewTransform tilt(double sin, double cos)
	{
		return new ViewTransform(
			1, 0, 0, 0,
			0, cos, -sin, 0,
			0, sin, cos, 0);
	}

	/// Right handed about a unit axis.
	static ViewTransform rotation(double axisX, double axisY, double axisZ, double angle)
	{
		double sin = Math.sin(angle);
		double cos = Math.cos(angle);
		double rest = 1 - cos;
		return new ViewTransform(
			cos + axisX * axisX * rest, axisX * axisY * rest - axisZ * sin, axisX * axisZ * rest + axisY * sin, 0,
			axisY * axisX * rest + axisZ * sin, cos + axisY * axisY * rest, axisY * axisZ * rest - axisX * sin, 0,
			axisZ * axisX * rest - axisY * sin, axisZ * axisY * rest + axisX * sin, cos + axisZ * axisZ * rest, 0);
	}

	ViewTransform then(ViewTransform next)
	{
		return new ViewTransform(
			next.xx * xx + next.xy * yx + next.xz * zx,
			next.xx * xy + next.xy * yy + next.xz * zy,
			next.xx * xz + next.xy * yz + next.xz * zz,
			next.xx * xw + next.xy * yw + next.xz * zw + next.xw,
			next.yx * xx + next.yy * yx + next.yz * zx,
			next.yx * xy + next.yy * yy + next.yz * zy,
			next.yx * xz + next.yy * yz + next.yz * zz,
			next.yx * xw + next.yy * yw + next.yz * zw + next.yw,
			next.zx * xx + next.zy * yx + next.zz * zx,
			next.zx * xy + next.zy * yy + next.zz * zy,
			next.zx * xz + next.zy * yz + next.zz * zz,
			next.zx * xw + next.zy * yw + next.zz * zw + next.zw);
	}

	double toCameraX(double x, double y, double z)
	{
		return xx * x + xy * y + xz * z + xw;
	}

	double toCameraY(double x, double y, double z)
	{
		return yx * x + yy * y + yz * z + yw;
	}

	double toDepth(double x, double y, double z)
	{
		return zx * x + zy * y + zz * z + zw;
	}
}
