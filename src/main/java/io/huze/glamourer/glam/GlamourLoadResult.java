package io.huze.glamourer.glam;

import java.util.ArrayList;
import lombok.Value;

@Value
public class GlamourLoadResult
{
	ArrayList<Glamour> loaded;
	ArrayList<GlamourData> failed;
}
