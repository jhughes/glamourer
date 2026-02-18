package io.huze.glamourer.glam;

public class GlamourConflictException extends IllegalStateException
{
	public GlamourConflictException(Glamour glamour, int id) {
		super(String.format("Item %s (%d) already has a glamour applied", glamour.getItemName(), id));
	}
}
