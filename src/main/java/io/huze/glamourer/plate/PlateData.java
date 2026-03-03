package io.huze.glamourer.plate;

import io.huze.glamourer.glam.GlamourData;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlateData
{
	private String id;
	private String name;
	private Boolean enabled;
	private Boolean expanded;
	private DisplayStyle displayStyle;
	private List<GlamourData> glamours;
}
