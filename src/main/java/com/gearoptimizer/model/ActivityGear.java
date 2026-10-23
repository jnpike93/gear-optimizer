package com.gearoptimizer.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class ActivityGear
{
	private String id;
	private String name;
	private String category;
	private String style;
	private Map<String, List<Integer>> slots;
	private List<InventoryItem> inventory;

	public ActivityGear()
	{
		this.slots = Collections.emptyMap();
		this.inventory = Collections.emptyList();
	}
}

