package com.gearoptimizer.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Collections;
import java.util.Map;

@Data
@AllArgsConstructor
public class GearDatabase
{
	private long version;
	private String last_updated;
	private Map<String, ActivityGear> activities;

	public GearDatabase()
	{
		this.activities = Collections.emptyMap();
	}
}

