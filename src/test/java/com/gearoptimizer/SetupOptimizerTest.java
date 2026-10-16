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

	@Test
	public void testVorkathMeleeDetectsBloodMoonAndDragonfireShield()
	{
		// Player owns Blood Moon gear (helm: 29028, chest: 29022, legs: 29025)
		// and Dragonfire shield (11283) or Antifire shield (1540)
		when(bankCache.hasItem(29028)).thenReturn(true);
		when(bankCache.hasItem(29022)).thenReturn(true);
		when(bankCache.hasItem(29025)).thenReturn(true);
		when(bankCache.hasItem(11283)).thenReturn(true);
		when(bankCache.hasItem(1540)).thenReturn(true);

		// Higher BIS tiers (Torva, Bandos, Avernic) are not owned
		when(bankCache.hasItem(26382)).thenReturn(false);
		when(bankCache.hasItem(26384)).thenReturn(false);
		when(bankCache.hasItem(11832)).thenReturn(false);
		when(bankCache.hasItem(26386)).thenReturn(false);
		when(bankCache.hasItem(11834)).thenReturn(false);
		when(bankCache.hasItem(22322)).thenReturn(false);

		Map<String, List<Integer>> slots = new HashMap<>();
		slots.put("head", Arrays.asList(26382, 23230, 29028, 10828));
		slots.put("body", Arrays.asList(26384, 11832, 29022, 10551));
		slots.put("legs", Arrays.asList(26386, 11834, 29025));
		slots.put("shield", Arrays.asList(22322, 11283, 12954, 1540));

		ActivityGear activity = new ActivityGear(
			"vorkath_melee", "Vorkath (Melee)", "Bossing", "Melee",
			slots,
			Collections.emptyList()
		);

		OptimizationResult result = optimizer.optimize(activity);

		// Blood moon gear correctly selected
		assertEquals(Integer.valueOf(29028), result.getEquippedItems().get("head"));
		assertEquals(Integer.valueOf(29022), result.getEquippedItems().get("body"));
		assertEquals(Integer.valueOf(29025), result.getEquippedItems().get("legs"));

		// Dragonfire shield correctly selected over antifire shield
		assertEquals(Integer.valueOf(11283), result.getEquippedItems().get("shield"));
	}

	@Test
	public void testDegradedBloodMoonArmorDetected()
	{
		// Player owns degraded/used Blood Moon armor:
		// Used helm: 29047, Used chest: 29043, Used tassets: 29045
		when(bankCache.hasItem(29047)).thenReturn(true);
		when(bankCache.hasItem(29043)).thenReturn(true);
		when(bankCache.hasItem(29045)).thenReturn(true);

		// Guide only lists clean/base IDs: 29028, 29022, 29025
		Map<String, List<Integer>> slots = new HashMap<>();
		slots.put("head", Arrays.asList(26382, 29028));
		slots.put("body", Arrays.asList(26384, 29022));
		slots.put("legs", Arrays.asList(26386, 29025));

		ActivityGear activity = new ActivityGear(
			"vorkath_melee", "Vorkath (Melee)", "Bossing", "Melee",
			slots,
			Collections.emptyList()
		);

		OptimizationResult result = optimizer.optimize(activity);

		// Degraded Blood Moon gear correctly detected and chosen
		assertEquals(Integer.valueOf(29047), result.getEquippedItems().get("head"));
		assertEquals(Integer.valueOf(29043), result.getEquippedItems().get("body"));
		assertEquals(Integer.valueOf(29045), result.getEquippedItems().get("legs"));
	}

	@Test
	public void testBarrowsPlatelegsInterchangeableDetection()
	{
		// Guide recommends Torag's platelegs (4751) as the representative "Barrows platelegs"
		Map<String, List<Integer>> slots = new HashMap<>();
		slots.put("legs", Arrays.asList(26386, 11834, 4751));

		// Scenario A: Player owns Dharok's platelegs 100 (4898)
		when(bankCache.hasItem(4898)).thenReturn(true);

		ActivityGear activity = new ActivityGear(
			"vorkath_melee", "Vorkath (Melee)", "Bossing", "Melee",
			slots,
			Collections.emptyList()
		);

		OptimizationResult result = optimizer.optimize(activity);

		// Dharok's platelegs 100 correctly selected as Barrows platelegs
		assertEquals(Integer.valueOf(4898), result.getEquippedItems().get("legs"));
	}

	@Test
	public void testAntifireShieldSelectedWhenDFSNotOwned()
	{
		// Player owns Anti-dragon / Antifire shield (1540), but not DFS (11283) or Avernic (22322)
		when(bankCache.hasItem(22322)).thenReturn(false);
		when(bankCache.hasItem(11283)).thenReturn(false);
		when(bankCache.hasItem(11284)).thenReturn(false);
		when(bankCache.hasItem(1540)).thenReturn(true);

		Map<String, List<Integer>> slots = new HashMap<>();
		slots.put("shield", Arrays.asList(22322, 11283, 12954, 1540));

		ActivityGear activity = new ActivityGear(
			"vorkath_melee", "Vorkath (Melee)", "Bossing", "Melee",
			slots,
			Collections.emptyList()
		);

		OptimizationResult result = optimizer.optimize(activity);

		assertEquals(Integer.valueOf(1540), result.getEquippedItems().get("shield"));
	}
}

