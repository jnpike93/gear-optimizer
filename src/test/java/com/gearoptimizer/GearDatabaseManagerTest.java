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
}

