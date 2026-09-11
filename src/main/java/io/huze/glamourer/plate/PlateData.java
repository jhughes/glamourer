package io.huze.glamourer.plate;

import io.huze.glamourer.glam.GlamourData;
import java.util.List;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlateData
{
	public static final int SUPPORTED_VERSION = 1;

	@Nullable
	private final Integer version = null;

	private String id;
	private String name;
	private Boolean enabled;
	private DisplayStyle displayStyle;
	private IconStyle iconStyle;
	private List<GlamourData> glamours;
}
