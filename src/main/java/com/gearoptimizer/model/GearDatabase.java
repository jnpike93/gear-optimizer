package com.gearoptimizer.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GearDatabase
{
	private long version;
	private String last_updated;
	private Map<String, ActivityGear> activities = Collections.emptyMap();
}

