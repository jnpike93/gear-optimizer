package com.gearoptimizer;

import com.gearoptimizer.SetupOptimizer.OptimizationResult;
import com.gearoptimizer.model.ActivityGear;
import com.gearoptimizer.model.InventoryItem;
import com.gearoptimizer.model.InventorySetupPortable;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.PluginManager;
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
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.Silent.class)
public class InventorySetupsExporterTest
{
	private final Gson gson = new Gson();

	@Mock
	private ConfigManager configManager;

	@Mock
	private PluginManager pluginManager;

	@Mock
	private GearOptimizerConfig config;

	@InjectMocks
	private InventorySetupsExporter exporter;

	@Before
	public void setUp() throws Exception
	{
		Field gsonField = InventorySetupsExporter.class.getDeclaredField("gson");
		gsonField.setAccessible(true);
		gsonField.set(exporter, gson);
	}

	@Test
	public void testExportPortableMatchesInventorySetupsSpecification()
	{
		Map<String, Integer> equipped = new HashMap<>();
		equipped.put("head", 26382);  // Torva helm -> slot 0
		equipped.put("cape", 21295);  // Infernal cape -> slot 1
		equipped.put("neck", 29801);  // Rancour -> slot 2
		equipped.put("weapon", 22325);// Scythe -> slot 3
		equipped.put("body", 26384);  // Torva body -> slot 4
		equipped.put("shield", 22322);// Avernic -> slot 5
		equipped.put("legs", 26386);  // Torva legs -> slot 7
		equipped.put("hands", 22981); // Ferocious -> slot 9
		equipped.put("boots", 13239); // Prims -> slot 10
		equipped.put("ring", 28307);  // Ultor -> slot 12

		List<InventoryItem> inv = Arrays.asList(
			new InventoryItem(2434, 3, false),
			new InventoryItem(391, 10, false)
		);

		ActivityGear activity = new ActivityGear(
			"toa_melee", "Tombs of Amascut (Melee)", "Raids", "Melee",
			Collections.emptyMap(),
			inv
		);

		OptimizationResult result = OptimizationResult.builder()
			.activity(activity)
			.equippedItems(equipped)
			.availableInventory(inv)
			.build();

		InventorySetupPortable portable = exporter.createPortableSetup(result);
		assertNotNull(portable);
		assertNotNull(portable.getSetup());
		assertNotNull(portable.getLayout());
		assertEquals("Tombs of Amascut (Melee)", portable.getSetup().getName());

		// Verify equipment list: 14 elements
		assertEquals(14, portable.getSetup().getEq().size());
		assertNotNull(portable.getSetup().getEq().get(0));
		assertEquals(26382, portable.getSetup().getEq().get(0).getId()); // head
		assertNotNull(portable.getSetup().getEq().get(3));
		assertEquals(22325, portable.getSetup().getEq().get(3).getId()); // weapon
		assertNull(portable.getSetup().getEq().get(6));                   // arms dummy
		assertNotNull(portable.getSetup().getEq().get(7));
		assertEquals(26386, portable.getSetup().getEq().get(7).getId()); // legs
		assertNull(portable.getSetup().getEq().get(8));                   // hair dummy

		// Verify inventory list: 28 elements
		assertEquals(28, portable.getSetup().getInv().size());
		assertNotNull(portable.getSetup().getInv().get(0));
		assertEquals(2434, portable.getSetup().getInv().get(0).getId());
		assertEquals(Integer.valueOf(3), portable.getSetup().getInv().get(0).getQ());
		assertNotNull(portable.getSetup().getInv().get(1));
		assertEquals(391, portable.getSetup().getInv().get(1).getId());
		assertNull(portable.getSetup().getInv().get(2)); // remaining slots are null

		// Verify JSON serialization format
		String json = exporter.exportToInventorySetupJson(result);
		assertNotNull(json);

		JsonObject root = gson.fromJson(json, JsonObject.class);
		assertTrue("Root must contain 'setup' key", root.has("setup"));
		assertTrue("Root must contain 'layout' key", root.has("layout"));

		JsonObject setupObj = root.getAsJsonObject("setup");
		assertTrue("Setup must contain 'name'", setupObj.has("name"));
		assertTrue("Setup must contain 'inv'", setupObj.has("inv"));
		assertTrue("Setup must contain 'eq'", setupObj.has("eq"));
		assertTrue("Setup must contain 'afi'", setupObj.has("afi"));
		assertEquals(14, setupObj.getAsJsonArray("eq").size());
		assertEquals(28, setupObj.getAsJsonArray("inv").size());
	}

	@Test
	public void testDirectImportPersistsToConfigManager()
	{
		ActivityGear activity = new ActivityGear(
			"toa_melee", "Tombs of Amascut (Melee)", "Raids", "Melee",
			Collections.emptyMap(),
			Collections.emptyList()
		);

		OptimizationResult result = OptimizationResult.builder()
			.activity(activity)
			.equippedItems(Collections.singletonMap("weapon", 22325))
			.availableInventory(Collections.emptyList())
			.build();

		boolean ok = exporter.directImportToInventorySetups(result);
		assertTrue("Direct import must return true on success", ok);

		org.mockito.Mockito.verify(configManager).setConfiguration(
			org.mockito.ArgumentMatchers.eq("inventorysetups"),
			org.mockito.ArgumentMatchers.startsWith("setupsV3_"),
			org.mockito.ArgumentMatchers.anyString()
		);
	}
}

