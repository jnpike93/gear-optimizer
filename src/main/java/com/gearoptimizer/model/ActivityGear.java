package com.gearoptimizer.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityGear
{
	private String id;
	private String name;
	private String category;
	private String style;
	private Map<String, List<Integer>> slots = Collections.emptyMap();
	private List<InventoryItem> inventory = Collections.emptyList();
}

