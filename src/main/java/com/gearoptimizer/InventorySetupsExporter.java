package com.gearoptimizer;

import com.gearoptimizer.SetupOptimizer.OptimizationResult;
import com.gearoptimizer.model.InventoryItem;
import com.gearoptimizer.model.InventorySetupExport;
import com.gearoptimizer.model.InventorySetupExport.SetupItem;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Singleton
public class InventorySetupsExporter
{
	@Inject
	private Gson gson;

	@Inject
	private ConfigManager configManager;

	public String exportToInventorySetupJson(OptimizationResult result)
	{
		if (result == null || result.getActivity() == null)
		{
			return null;
		}

		List<SetupItem> equipList = new ArrayList<>();
		for (Map.Entry<String, Integer> equipEntry : result.getEquippedItems().entrySet())
		{
			equipList.add(new SetupItem(equipEntry.getValue(), 1));
		}

		List<SetupItem> invList = new ArrayList<>();
		for (InventoryItem invItem : result.getAvailableInventory())
		{
			invList.add(new SetupItem(invItem.getId(), invItem.getQty()));
		}

		InventorySetupExport setup = InventorySetupExport.builder()
			.name(result.getActivity().getName() + " (Auto)")
			.notes("Optimized by RuneLite Gear Optimizer from OSRS Wiki recommendations")
			.equip(equipList)
			.inv(invList)
			.runePouch(new ArrayList<>())
			.boltPouch(new ArrayList<>())
			.spellBook(0)
			.build();

		return gson.toJson(setup);
	}

	public boolean copySetupToClipboard(OptimizationResult result)
	{
		String json = exportToInventorySetupJson(result);
		if (json == null)
		{
			return false;
		}

		try
		{
			StringSelection selection = new StringSelection(json);
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			clipboard.setContents(selection, selection);
			log.info("Exported setup for '{}' to clipboard", result.getActivity().getName());
			return true;
		}
		catch (Exception e)
		{
			log.error("Failed to copy setup to clipboard", e);
			return false;
		}
	}
}

