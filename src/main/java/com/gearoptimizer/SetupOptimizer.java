package com.gearoptimizer;

import com.gearoptimizer.model.ActivityGear;
import com.gearoptimizer.model.InventoryItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
public class SetupOptimizer
{
	@Inject
	private ItemManager itemManager;

	@Inject
	private PersistentBankCache bankCache;

	// Barrows melee platelegs (Torag, Dharok, Verac, Guthan - undegraded and degraded)
	public static final Set<Integer> BARROWS_PLATELEGS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		// Torag's platelegs
		4751, 4970, 4971, 4972, 4973, 4974, 23634,
		// Dharok's platelegs
		4722, 4898, 4899, 4900, 4901, 4902, 23633,
		// Verac's plateskirt
		4759, 4994, 4995, 4996, 4997, 4998, 23635,
		// Guthan's chainskirt
		4730, 4922, 4923, 4924, 4925, 4926
	)));

	// Barrows melee platebodies (Torag, Dharok, Guthan, Verac - undegraded and degraded)
	public static final Set<Integer> BARROWS_PLATEBODIES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		// Torag's platebody
		4749, 4964, 4965, 4966, 4967, 4968,
		// Dharok's platebody
		4720, 4892, 4893, 4894, 4895, 4896, 25515,
		// Guthan's platebody
		4728, 4916, 4917, 4918, 4919, 4920,
		// Verac's brassard
		4757, 4988, 4989, 4990, 4991, 4992, 27190
	)));

	// Barrows melee helms (Torag, Dharok, Guthan, Verac - undegraded and degraded)
	public static final Set<Integer> BARROWS_HELMS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		// Torag's helm
		4745, 4952, 4953, 4954, 4955, 4956, 23637,
		// Dharok's helm
		4716, 4880, 4881, 4882, 4883, 4884, 23639,
		// Guthan's helm
		4724, 4904, 4905, 4906, 4907, 4908, 23638,
		// Verac's helm
		4753, 4976, 4977, 4978, 4979, 4980, 23636
	)));

	// Blood Moon armor pieces (clean, used, 0%, broken)
	public static final Set<Integer> BLOOD_MOON_HELM = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		29028, 29047, 29073, 29848
	)));

	public static final Set<Integer> BLOOD_MOON_CHEST = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		29022, 29043, 29067, 29846
	)));

	public static final Set<Integer> BLOOD_MOON_TASSETS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		29025, 29045, 29070, 29847
	)));

	// Dragonfire shields
	public static final Set<Integer> DRAGONFIRE_SHIELDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		11283, 11284, 33186
	)));

	// Antifire / Anti-dragon shields
	public static final Set<Integer> ANTIFIRE_SHIELDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		1540, 28731
	)));

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

	public Integer resolveOwnedItem(int candidateItemId)
	{
		// 1. Direct check
		if (bankCache.hasItem(candidateItemId))
		{
			return candidateItemId;
		}

		// 2. Canonical check
		int canonical = itemManager != null ? itemManager.canonicalize(candidateItemId) : candidateItemId;
		if (bankCache.hasItem(canonical))
		{
			return canonical;
		}

		// 3. ItemVariationMapping check
		try
		{
			int mappedBase = ItemVariationMapping.map(canonical);
			if (bankCache.hasItem(mappedBase))
			{
				return mappedBase;
			}

			Collection<Integer> variations = ItemVariationMapping.getVariations(mappedBase);
			if (variations != null && !variations.isEmpty())
			{
				for (int varId : variations)
				{
					if (bankCache.hasItem(varId))
					{
						return varId;
					}
				}
			}

			int rawMapped = ItemVariationMapping.map(candidateItemId);
			if (rawMapped != mappedBase)
			{
				if (bankCache.hasItem(rawMapped))
				{
					return rawMapped;
				}
				Collection<Integer> rawVars = ItemVariationMapping.getVariations(rawMapped);
				if (rawVars != null && !rawVars.isEmpty())
				{
					for (int varId : rawVars)
					{
						if (bankCache.hasItem(varId))
						{
							return varId;
						}
					}
				}
			}
		}
		catch (Exception ignored)
		{
			// Fall through if ItemVariationMapping is not available
		}

		// 4. Blood Moon armor fallback
		if (BLOOD_MOON_HELM.contains(candidateItemId) || BLOOD_MOON_HELM.contains(canonical))
		{
			for (int id : BLOOD_MOON_HELM)
			{
				if (bankCache.hasItem(id)) return id;
			}
		}
		if (BLOOD_MOON_CHEST.contains(candidateItemId) || BLOOD_MOON_CHEST.contains(canonical))
		{
			for (int id : BLOOD_MOON_CHEST)
			{
				if (bankCache.hasItem(id)) return id;
			}
		}
		if (BLOOD_MOON_TASSETS.contains(candidateItemId) || BLOOD_MOON_TASSETS.contains(canonical))
		{
			for (int id : BLOOD_MOON_TASSETS)
			{
				if (bankCache.hasItem(id)) return id;
			}
		}

		// 5. Dragonfire shield fallback
		if (DRAGONFIRE_SHIELDS.contains(candidateItemId) || DRAGONFIRE_SHIELDS.contains(canonical))
		{
			for (int id : DRAGONFIRE_SHIELDS)
			{
				if (bankCache.hasItem(id)) return id;
			}
		}

		// 6. Anti-dragon shield fallback
		if (ANTIFIRE_SHIELDS.contains(candidateItemId) || ANTIFIRE_SHIELDS.contains(canonical))
		{
			for (int id : ANTIFIRE_SHIELDS)
			{
				if (bankCache.hasItem(id)) return id;
			}
		}

		// 7. Barrows sets check
		if (BARROWS_PLATELEGS.contains(candidateItemId) || BARROWS_PLATELEGS.contains(canonical))
		{
			for (int barrowsLegId : BARROWS_PLATELEGS)
			{
				if (bankCache.hasItem(barrowsLegId))
				{
					return barrowsLegId;
				}
			}
		}

		if (BARROWS_PLATEBODIES.contains(candidateItemId) || BARROWS_PLATEBODIES.contains(canonical))
		{
			for (int barrowsBodyId : BARROWS_PLATEBODIES)
			{
				if (bankCache.hasItem(barrowsBodyId))
				{
					return barrowsBodyId;
				}
			}
		}

		if (BARROWS_HELMS.contains(candidateItemId) || BARROWS_HELMS.contains(canonical))
		{
			for (int barrowsHelmId : BARROWS_HELMS)
			{
				if (bankCache.hasItem(barrowsHelmId))
				{
					return barrowsHelmId;
				}
			}
		}

		return null;
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
				Integer owned = resolveOwnedItem(itemId);
				if (owned != null)
				{
					chosenItemId = owned;
					break;
				}
			}

			if (chosenItemId != null)
			{
				result.getEquippedItems().put(slot, chosenItemId);

				// If chosen item is not the BIS, track BIS upgrade
				Integer ownedBis = resolveOwnedItem(bisItemId);
				if (ownedBis == null || !ownedBis.equals(chosenItemId))
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

		// If chosen weapon is two-handed, suppress shield slot
		Integer equippedWeapon = result.getEquippedItems().get("weapon");
		if (equippedWeapon != null && isTwoHandedWeapon(equippedWeapon))
		{
			result.getEquippedItems().remove("shield");
			result.getMissingBisItems().remove("shield");
		}

		// 2. Resolve Inventory Supplies
		if (activity.getInventory() != null)
		{
			for (InventoryItem invItem : activity.getInventory())
			{
				Integer ownedId = resolveOwnedItem(invItem.getId());
				if (ownedId != null)
				{
					result.getAvailableInventory().add(new InventoryItem(ownedId, invItem.getQty(), invItem.isOptional()));
				}
				else
				{
					result.getMissingInventory().add(invItem);
				}
			}
		}

		return result;
	}

	public boolean isTwoHandedWeapon(int itemId)
	{
		if (itemManager == null)
		{
			return false;
		}

		try
		{
			net.runelite.client.game.ItemStats stats = itemManager.getItemStats(itemId);
			if (stats != null && stats.getEquipment() != null)
			{
				return stats.getEquipment().isTwoHanded();
			}
		}
		catch (Exception ignored) {}

		return false;
	}
}

