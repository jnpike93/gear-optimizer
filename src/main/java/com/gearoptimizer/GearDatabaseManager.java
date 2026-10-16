package com.gearoptimizer;

import com.gearoptimizer.model.ActivityGear;
import com.gearoptimizer.model.GearDatabase;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@Singleton
public class GearDatabaseManager
{
	private static final String DEFAULT_REMOTE_URL = "https://raw.githubusercontent.com/jnpike93/gear-optimizer/main/data/gear_database.json";
	private static final File DIRECTORY = new File(RuneLite.RUNELITE_DIR, "gear-optimizer");
	private static final File CACHE_FILE = new File(DIRECTORY, "gear_database.json");

	private static final String CONFIG_GROUP = "gearoptimizer";
	private static final String KEY_ETAG = "db_etag";
	private static final String KEY_LAST_CHECK = "db_last_check";

	@Inject
	private Gson gson;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private ConfigManager configManager;

	private GearDatabase database;

	public void init()
	{
		loadLocalDatabase();
		executor.submit(this::checkRemoteUpdate);
	}

	public synchronized void loadLocalDatabase()
	{
		if (CACHE_FILE.exists())
		{
			try (Reader reader = new FileReader(CACHE_FILE))
			{
				GearDatabase loaded = gson.fromJson(reader, GearDatabase.class);
				if (loaded != null && loaded.getActivities() != null && !loaded.getActivities().isEmpty())
				{
					this.database = loaded;
					log.info("Loaded gear database from disk cache (version: {})", loaded.getVersion());
					return;
				}
			}
			catch (Exception e)
			{
				log.warn("Corrupted disk cache, falling back to bundled resources", e);
			}
		}

		// Fallback: Read bundled snapshot inside the JAR
		try (InputStream in = getClass().getResourceAsStream("/gear_database.json");
		     Reader reader = new InputStreamReader(in))
		{
			this.database = gson.fromJson(reader, GearDatabase.class);
			log.info("Loaded bundled gear database from JAR (version: {})", database.getVersion());
		}
		catch (Exception e)
		{
			log.error("Failed to load bundled gear database", e);
			this.database = new GearDatabase(0, "error", Collections.emptyMap());
		}
	}

	public void checkRemoteUpdate()
	{
		try
		{
			// Check if 7 days have passed since last check
			String lastCheckStr = configManager.getConfiguration(CONFIG_GROUP, KEY_LAST_CHECK);
			if (lastCheckStr != null)
			{
				try
				{
					Instant lastCheck = Instant.parse(lastCheckStr);
					if (Instant.now().isBefore(lastCheck.plus(7, ChronoUnit.DAYS)))
					{
						log.debug("Skipping gear database update check: last check was recent ({})", lastCheck);
						return;
					}
				}
				catch (Exception ignored) {}
			}

			String cachedEtag = configManager.getConfiguration(CONFIG_GROUP, KEY_ETAG);
			Request.Builder requestBuilder = new Request.Builder().url(DEFAULT_REMOTE_URL);

			if (cachedEtag != null && !cachedEtag.isEmpty())
			{
				requestBuilder.header("If-None-Match", cachedEtag);
			}

			try (Response response = okHttpClient.newCall(requestBuilder.build()).execute())
			{
				configManager.setConfiguration(CONFIG_GROUP, KEY_LAST_CHECK, Instant.now().toString());

				if (response.code() == 304)
				{
					log.info("Gear database is already up to date (304 Not Modified)");
					return;
				}

				if (response.isSuccessful() && response.body() != null)
				{
					String json = response.body().string();
					GearDatabase remote = gson.fromJson(json, GearDatabase.class);

					if (remote != null && (database == null || remote.getVersion() > database.getVersion()))
					{
						DIRECTORY.mkdirs();
						Files.writeString(CACHE_FILE.toPath(), json);
						this.database = remote;

						String newEtag = response.header("ETag");
						if (newEtag != null)
						{
							configManager.setConfiguration(CONFIG_GROUP, KEY_ETAG, newEtag);
						}

						log.info("Updated gear database to version {}", remote.getVersion());
					}
				}
			}
		}
		catch (Exception e)
		{
			log.debug("Unable to perform remote gear database check (operating fully offline): {}", e.getMessage());
		}
	}

	public Map<String, ActivityGear> getActivities()
	{
		return database != null ? database.getActivities() : Collections.emptyMap();
	}

	public List<ActivityGear> getAllActivities()
	{
		if (database == null || database.getActivities() == null)
		{
			return Collections.emptyList();
		}
		List<ActivityGear> list = new ArrayList<>(database.getActivities().values());
		list.sort(Comparator.comparing(ActivityGear::getName));
		return list;
	}

	public List<ActivityGear> getActivitiesByStyle(String style)
	{
		if (database == null || database.getActivities() == null)
		{
			return Collections.emptyList();
		}
		if (style == null || style.equalsIgnoreCase("all") || style.equalsIgnoreCase("all styles"))
		{
			return getAllActivities();
		}
		List<ActivityGear> filtered = new ArrayList<>();
		for (ActivityGear act : database.getActivities().values())
		{
			if (style.equalsIgnoreCase(act.getStyle()))
			{
				filtered.add(act);
			}
		}
		filtered.sort(Comparator.comparing(ActivityGear::getName));
		return filtered;
	}

	public ActivityGear getActivity(String id)
	{
		return database != null && database.getActivities() != null ? database.getActivities().get(id) : null;
	}

	public long getVersion()
	{
		return database != null ? database.getVersion() : 0;
	}
}

