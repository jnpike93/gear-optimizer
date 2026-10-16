package com.gearoptimizer;

import com.gearoptimizer.model.ActivityGear;
import com.gearoptimizer.model.InventoryItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.runelite.client.game.ItemManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class SetupOptimizer
{
	@Inject
	private ItemManager itemManager;

	@Inject
	private PersistentBankCache bankCache;

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class OptimizationResult
	{
		private ActivityGear activity;

		@Builder.Default
		private Map<String, Integer> equippedItems = new HashMap<>();

		@Builder.Default
		private Map<String, Integer> missingBisItems = new HashMap<>();

		@Builder.Default
		private List<InventoryItem> availableInventory = new ArrayList<>();

		@Builder.Default
		private List<InventoryItem> missingInventory = new ArrayList<>();
	}

	public OptimizationResult optimize(ActivityGear activity)
	{
		OptimizationResult result = OptimizationResult.builder()
			.activity(activity)
			.build();

		if (activity == null || activity.getSlots() == null)
		{
			return result;
		}

		// 1. Resolve Equipment Slots
		for (Map.Entry<String, List<Integer>> entry : activity.getSlots().entrySet())
		{
			String slot = entry.getKey();
			List<Integer> rankedOptions = entry.getValue();

			if (rankedOptions == null || rankedOptions.isEmpty())
			{
				continue;
			}

			int bisItemId = rankedOptions.get(0);
			Integer chosenItemId = null;

			for (int itemId : rankedOptions)
			{
				int canonical = itemManager.canonicalize(itemId);
				if (bankCache.hasItem(canonical))
				{
					chosenItemId = itemId;
					break;
				}
			}

			if (chosenItemId != null)
			{
				result.getEquippedItems().put(slot, chosenItemId);

				// If the chosen item is not the #1 Best-in-Slot, track the BIS upgrade
				if (chosenItemId != bisItemId)
				{
					result.getMissingBisItems().put(slot, bisItemId);
				}
			}
			else
			{
				// Player owns no items recommended for this slot
				result.getMissingBisItems().put(slot, bisItemId);
			}
		}

		// 2. Resolve Inventory Supplies
		if (activity.getInventory() != null)
		{
			for (InventoryItem invItem : activity.getInventory())
			{
				int canonical = itemManager.canonicalize(invItem.getId());
				if (bankCache.hasItem(canonical))
				{
					result.getAvailableInventory().add(invItem);
				}
				else
				{
					result.getMissingInventory().add(invItem);
				}
			}
		}

		return result;
	}
}

