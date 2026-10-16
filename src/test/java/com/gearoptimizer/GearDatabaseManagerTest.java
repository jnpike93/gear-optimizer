package com.gearoptimizer;

import com.gearoptimizer.model.ActivityGear;
import com.google.gson.Gson;
import net.runelite.client.config.ConfigManager;
import okhttp3.OkHttpClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.Silent.class)
public class GearDatabaseManagerTest
{
	private final Gson gson = new Gson();

	@Mock
	private OkHttpClient okHttpClient;

	@Mock
	private ScheduledExecutorService executor;

	@Mock
	private ConfigManager configManager;

	@InjectMocks
	private GearDatabaseManager dbManager;

	@Before
	public void setUp() throws Exception
	{
		Field gsonField = GearDatabaseManager.class.getDeclaredField("gson");
		gsonField.setAccessible(true);
		gsonField.set(dbManager, gson);

		dbManager.loadLocalDatabase();
	}

	@Test
	public void testBundledDatabaseLoadsActivitiesWithCombatStyles()
	{
		List<ActivityGear> all = dbManager.getAllActivities();
		assertFalse("Activities should be loaded from bundled JSON", all.isEmpty());

		List<ActivityGear> melee = dbManager.getActivitiesByStyle("Melee");
		List<ActivityGear> ranged = dbManager.getActivitiesByStyle("Ranged");
		List<ActivityGear> magic = dbManager.getActivitiesByStyle("Magic");

		assertFalse("Melee activities should not be empty", melee.isEmpty());
		assertFalse("Ranged activities should not be empty", ranged.isEmpty());
		assertFalse("Magic activities should not be empty", magic.isEmpty());

		assertTrue("Total activities should be sum of styles", all.size() >= melee.size() + ranged.size() + magic.size());

		// Verify every filtered activity has correct style
		for (ActivityGear act : melee)
		{
			assertEquals("Melee", act.getStyle());
		}
		for (ActivityGear act : ranged)
		{
			assertEquals("Ranged", act.getStyle());
		}
		for (ActivityGear act : magic)
		{
			assertEquals("Magic", act.getStyle());
		}
	}

	@Test
	public void testBossingAndSlayerCategoryFiltering()
	{
		List<ActivityGear> bossing = dbManager.getActivities("Bossing", "All Styles");
		List<ActivityGear> slayer = dbManager.getActivities("Slayer", "All Styles");
		List<ActivityGear> raids = dbManager.getActivities("Raids", "All Styles");

		assertFalse("Bossing activities should be populated", bossing.isEmpty());
		assertFalse("Slayer activities should be populated", slayer.isEmpty());
		assertFalse("Raids activities should be populated", raids.isEmpty());

		assertTrue("Should have extensive bossing setups (>= 100)", bossing.size() >= 100);
		assertTrue("Should have extensive slayer setups (>= 20)", slayer.size() >= 20);

		// Verify Vorkath is present in bossing
		boolean hasVorkath = bossing.stream().anyMatch(a -> a.getName().toLowerCase().contains("vorkath"));
		assertTrue("Bossing should include Vorkath", hasVorkath);

		// Verify Gargoyles, Abyssal demons, or Dust devils is present in slayer
		boolean hasGargoyles = slayer.stream().anyMatch(a -> a.getName().toLowerCase().contains("gargoyle"));
		boolean hasAbyssalDemons = slayer.stream().anyMatch(a -> a.getName().toLowerCase().contains("abyssal demon"));
		boolean hasDustDevils = slayer.stream().anyMatch(a -> a.getName().toLowerCase().contains("dust devil"));
		assertTrue("Slayer tasks should include Gargoyles", hasGargoyles);
		assertTrue("Slayer tasks should include Abyssal demons", hasAbyssalDemons);
		assertTrue("Slayer tasks should include Dust devils", hasDustDevils);

		// Test combined category and style filter
		List<ActivityGear> slayerMagic = dbManager.getActivities("Slayer", "Magic");
		assertFalse("Slayer magic setups should exist (e.g. bursting/barraging)", slayerMagic.isEmpty());
		for (ActivityGear act : slayerMagic)
		{
			assertEquals("Slayer", act.getCategory());
			assertEquals("Magic", act.getStyle());
		}
	}
}

