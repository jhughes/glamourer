package io.huze.glamourer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class Extensions
{
	public static <T> List<T> mutable(List<T> list)
	{
		if (list instanceof ArrayList) return list;
		return new ArrayList<>(list);
	}

	public static short[] deepCopy(short[] array)
	{
		return array == null ? null : Arrays.copyOf(array, array.length);
	}

	public static <T extends Collection<Integer>> int[] toIntArray(T collection)
	{
		int[] array = new int[collection.size()];
		int i = 0;
		for (int x : collection)
		{
			array[i++] = x;
		}
		return array;
	}

	public static <T extends Collection<Short>> short[] toShortArray(T collection)
	{
		short[] array = new short[collection.size()];
		int i = 0;
		for (short s : collection)
		{
			array[i++] = s;
		}
		return array;
	}

	public static String toHex(short[] array)
	{
		if (array == null)
		{
			return "";
		}

		StringBuilder sb = new StringBuilder(array.length * 4);
		for (short s : array)
		{
			sb.append(String.format("%04x", s & 0xFFFF));
		}
		return sb.toString();
	}

	public static String toHex(Collection<Short> collection)
	{
		if (collection == null)
		{
			return "";
		}

		StringBuilder sb = new StringBuilder(collection.size() * 4);
		for (short s : collection)
		{
			sb.append(String.format("%04x", s & 0xFFFF));
		}
		return sb.toString();
	}
}
