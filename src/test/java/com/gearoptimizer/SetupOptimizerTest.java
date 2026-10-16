package com.gearoptimizer;

import com.gearoptimizer.SetupOptimizer.OptimizationResult;
import com.gearoptimizer.model.ActivityGear;
import com.gearoptimizer.model.InventoryItem;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class SetupOptimizerTest
{
	@Mock
	private ItemManager itemManager;

	@Mock
	private PersistentBankCache bankCache;

	@InjectMocks
	private SetupOptimizer optimizer;

	@Before
	public void setUp()
	{
		// Default mock: canonicalize returns the same ID
		when(itemManager.canonicalize(anyInt())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	public void testOptimizerPicksBestOwnedItem()
	{
		// Weapon options: 22324 (DHCB - BIS), 21902 (Dragon Xbow - 2nd), 12269 (Rune Xbow - 3rd)
		// Player owns 21902 and 12269 (no DHCB)
		when(bankCache.hasItem(22324)).thenReturn(false);
		when(bankCache.hasItem(21902)).thenReturn(true);
		when(bankCache.hasItem(12269)).thenReturn(true);

		Map<String, List<Integer>> slots = new HashMap<>();
		slots.put("weapon", Arrays.asList(22324, 21902, 12269));

		ActivityGear activity = new ActivityGear(
			"vorkath", "Vorkath", "Bossing", "Ranged",
			slots,
			Collections.emptyList()
		);

		OptimizationResult result = optimizer.optimize(activity);

		assertNotNull(result);
		assertEquals(Integer.valueOf(21902), result.getEquippedItems().get("weapon"));
		// DHCB should be tracked as a missing BIS upgrade
		assertEquals(Integer.valueOf(22324), result.getMissingBisItems().get("weapon"));
	}

	@Test
	public void testOptimizerWhenPlayerOwnsBestInSlot()
	{
		// Player owns DHCB
		when(bankCache.hasItem(22324)).thenReturn(true);

		Map<String, List<Integer>> slots = new HashMap<>();
		slots.put("weapon", Arrays.asList(22324, 21902, 12269));

		ActivityGear activity = new ActivityGear(
			"vorkath", "Vorkath", "Bossing", "Ranged",
			slots,
			Collections.emptyList()
		);

		OptimizationResult result = optimizer.optimize(activity);

		assertEquals(Integer.valueOf(22324), result.getEquippedItems().get("weapon"));
		// No missing BIS upgrade since they already have #1
		assertNull(result.getMissingBisItems().get("weapon"));
	}

	@Test
	public void testOptimizerCategorizesInventorySupplies()
	{
		// Prayer pot (2434) owned, Antifire (2452) missing
		when(bankCache.hasItem(2434)).thenReturn(true);
		when(bankCache.hasItem(2452)).thenReturn(false);

		List<InventoryItem> inventory = Arrays.asList(
			new InventoryItem(2434, 3, false),
			new InventoryItem(2452, 1, false)
		);

		ActivityGear activity = new ActivityGear(
			"vorkath", "Vorkath", "Bossing", "Ranged",
			Collections.emptyMap(),
			inventory
		);

		OptimizationResult result = optimizer.optimize(activity);

		assertEquals(1, result.getAvailableInventory().size());
		assertEquals(2434, result.getAvailableInventory().get(0).getId());
		assertEquals(1, result.getMissingInventory().size());
		assertEquals(2452, result.getMissingInventory().get(0).getId());
	}
}

