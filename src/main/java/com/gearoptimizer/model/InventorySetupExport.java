package com.gearoptimizer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventorySetupExport
{
	private String name;
	private String notes;

	@Builder.Default
	private List<SetupItem> inv = new ArrayList<>();

	@Builder.Default
	private List<SetupItem> equip = new ArrayList<>();

	@Builder.Default
	private List<SetupItem> runePouch = new ArrayList<>();

	@Builder.Default
	private List<SetupItem> boltPouch = new ArrayList<>();

	@Builder.Default
	private int spellBook = 0;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SetupItem
	{
		private int id;
		private int q;
	}
}

