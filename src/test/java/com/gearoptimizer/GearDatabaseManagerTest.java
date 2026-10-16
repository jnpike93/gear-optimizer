package com.gearoptimizer;

import com.gearoptimizer.model.ActivityGear;
import com.google.gson.Gson;
import net.runelite.client.config.ConfigManager;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
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
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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

	@Mock
	private GearOptimizerConfig config;

	@InjectMocks
	private GearDatabaseManager dbManager;

	@Before
	public void setUp() throws Exception
	{
		when(config.autoSync()).thenReturn(true);

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

	@Test
	public void testActivitiesContainExtractedInventoryPotionsAndRequiredTools()
	{
		// Vorkath setup checks: must contain Crumble Undead autocasting staff (4170) and Antifire/Antivenom (22209/29824)
		ActivityGear vorkathMelee = dbManager.getActivity("vorkath_melee");
		assertNotNull("Vorkath melee activity should exist", vorkathMelee);
		assertFalse("Vorkath inventory should not be empty", vorkathMelee.getInventory().isEmpty());
		boolean hasStaff = vorkathMelee.getInventory().stream().anyMatch(i -> i.getId() == 4170 || i.getId() == 11791);
		boolean hasAntifire = vorkathMelee.getInventory().stream().anyMatch(i -> i.getId() == 22209 || i.getId() == 2452);
		assertTrue("Vorkath inventory should include staff for Crumble Undead", hasStaff);
		assertTrue("Vorkath inventory should include antifire potion", hasAntifire);

		// Duke Sucellus checks: must contain pickaxe (23680 / 11920) and light source (20720)
		ActivityGear duke = dbManager.getActivity("duke_sucellus_melee");
		assertNotNull("Duke Sucellus activity should exist", duke);
		boolean hasPickaxe = duke.getInventory().stream().anyMatch(i -> i.getId() == 23680 || i.getId() == 11920 || i.getId() == 1275);
		boolean hasLight = duke.getInventory().stream().anyMatch(i -> i.getId() == 20720 || i.getId() == 4550);
		assertTrue("Duke Sucellus inventory should include pickaxe for mining salt", hasPickaxe);
		assertTrue("Duke Sucellus inventory should include light source", hasLight);

		// Gargoyles slayer task: must contain rock hammer (4162)
		ActivityGear gargoyles = dbManager.getActivity("slayer_gargoyles_melee");
		assertNotNull("Gargoyles activity should exist", gargoyles);
		boolean hasHammer = gargoyles.getInventory().stream().anyMatch(i -> i.getId() == 4162);
		assertTrue("Gargoyles inventory should include rock hammer", hasHammer);

		// Desert lizards slayer task: must contain ice coolers (6696)
		ActivityGear lizards = dbManager.getActivity("slayer_desert_lizards_melee");
		assertNotNull("Desert lizards activity should exist", lizards);
		boolean hasCooler = lizards.getInventory().stream().anyMatch(i -> i.getId() == 6696 && i.getQty() >= 10);
		assertTrue("Desert lizards inventory should include ice coolers", hasCooler);
	}

	@Test
	public void testThirtyDayTtlAndManualForceUpdate() throws Exception
	{
		Call call = mock(Call.class);
		Response response = new Response.Builder()
				.request(new Request.Builder().url("https://raw.githubusercontent.com/test/gear.json").build())
				.protocol(Protocol.HTTP_1_1)
				.code(304)
				.message("Not Modified")
				.build();
		when(call.execute()).thenReturn(response);
		when(okHttpClient.newCall(any())).thenReturn(call);

		// Case 1: Last check was 5 days ago (< 30 days) and force=false -> Should skip HTTP call
		Instant recentCheck = Instant.now().minus(5, ChronoUnit.DAYS);
		when(configManager.getConfiguration("gearoptimizer", "db_last_check")).thenReturn(recentCheck.toString());

		dbManager.checkRemoteUpdate(false);
		verify(okHttpClient, never()).newCall(any());

		// Case 2: Manual force update (force=true) -> Should bypass 30-day TTL and execute HTTP call
		dbManager.checkRemoteUpdate(true);
		verify(okHttpClient, times(1)).newCall(any());

		// Case 3: Last check was 35 days ago (> 30 days) and force=false -> Should execute HTTP call
		Instant expiredCheck = Instant.now().minus(35, ChronoUnit.DAYS);
		when(configManager.getConfiguration("gearoptimizer", "db_last_check")).thenReturn(expiredCheck.toString());

		dbManager.checkRemoteUpdate(false);
		verify(okHttpClient, times(2)).newCall(any());
	}

	@Test
	public void testAutoSyncDisabledSkipsRemoteUpdate() throws Exception
	{
		when(config.autoSync()).thenReturn(false);

		// Expired check (> 30 days), but autoSync is disabled
		Instant expiredCheck = Instant.now().minus(35, ChronoUnit.DAYS);
		when(configManager.getConfiguration("gearoptimizer", "db_last_check")).thenReturn(expiredCheck.toString());

		dbManager.checkRemoteUpdate(false);
		verify(okHttpClient, never()).newCall(any());

		// Manual force update should still work even if autoSync is disabled
		Call call = mock(Call.class);
		Response response = new Response.Builder()
				.request(new Request.Builder().url("https://raw.githubusercontent.com/test/gear.json").build())
				.protocol(Protocol.HTTP_1_1)
				.code(304)
				.message("Not Modified")
				.build();
		when(call.execute()).thenReturn(response);
		when(okHttpClient.newCall(any())).thenReturn(call);

		dbManager.checkRemoteUpdate(true);
		verify(okHttpClient, times(1)).newCall(any());
	}

	@Test
	public void testExcessiveRemotePayloadRejected() throws Exception
	{
		Call call = mock(Call.class);
		ResponseBody mockBody = mock(ResponseBody.class);
		// 15 MB payload exceeds the 10 MB security budget
		when(mockBody.contentLength()).thenReturn(15L * 1024 * 1024);

		Response response = new Response.Builder()
				.request(new Request.Builder().url("https://raw.githubusercontent.com/test/gear.json").build())
				.protocol(Protocol.HTTP_1_1)
				.code(200)
				.message("OK")
				.body(mockBody)
				.build();
		when(call.execute()).thenReturn(response);
		when(okHttpClient.newCall(any())).thenReturn(call);

		long versionBefore = dbManager.getVersion();
		dbManager.checkRemoteUpdate(true);

		// Verify source() was never consumed due to payload size limit
		verify(mockBody, never()).source();
		assertEquals("Database version should remain unchanged after rejecting oversized payload",
				versionBefore, dbManager.getVersion());
	}
}

