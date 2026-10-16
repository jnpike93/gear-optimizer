package com.gearoptimizer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Portable representation of an Inventory Setup matching dillydill123/inventory-setups
 * portable JSON specification for 100% clipboard and direct-import compatibility.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventorySetupPortable
{
	@Builder.Default
	private InventorySetupSerializable setup = new InventorySetupSerializable();

	@Builder.Default
	private int[] layout = new int[0];

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class InventorySetupSerializable
	{
		@Builder.Default
		private List<InventorySetupItemSerializable> inv = new ArrayList<>();

		@Builder.Default
		private List<InventorySetupItemSerializable> eq = new ArrayList<>();

		private List<InventorySetupItemSerializable> rp;

		private List<InventorySetupItemSerializable> bp;

		private List<InventorySetupItemSerializable> qv;

		@Builder.Default
		private Map<Integer, InventorySetupItemSerializable> afi = new HashMap<>();

		private String name;

		private String notes;

		private Object hc;

		@Builder.Default
		private Boolean hd = Boolean.FALSE;

		private Object dc;

		@Builder.Default
		private Boolean fb = Boolean.TRUE;

		@Builder.Default
		private Boolean uh = Boolean.FALSE;

		@Builder.Default
		private Integer sb = 0;

		@Builder.Default
		private Boolean fv = Boolean.FALSE;

		private Integer iId;

		@Builder.Default
		private String ao = "";
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class InventorySetupItemSerializable
	{
		private int id;
		private Integer q;
		private Boolean f;
		private String sc;

		public InventorySetupItemSerializable(int id)
		{
			this.id = id;
			this.q = null;
			this.f = null;
			this.sc = null;
		}

		public InventorySetupItemSerializable(int id, Integer q)
		{
			this.id = id;
			this.q = (q != null && q != 1) ? q : null;
			this.f = null;
			this.sc = null;
		}
	}
}

