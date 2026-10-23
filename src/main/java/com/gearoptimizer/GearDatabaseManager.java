package com.gearoptimizer;

import com.gearoptimizer.model.ActivityGear;
import com.gearoptimizer.model.GearDatabase;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Singleton
public class GearDatabaseManager
{
	private static final String DEFAULT_REMOTE_URL = "https://raw.githubusercontent.com/jnpike93/gear-optimizer/main/data/gear_database.json";
	private static final File DIRECTORY = new File(RuneLite.RUNELITE_DIR, "gear-optimizer");
	private static final File CACHE_FILE = new File(DIRECTORY, "gear_database.json");
	private static final File TEMP_CACHE_FILE = new File(DIRECTORY, "gear_database.json.tmp");
	private static final long MAX_REMOTE_PAYLOAD_BYTES = 10 * 1024 * 1024; // 10 MB maximum payload limit

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

	@Inject
	private GearOptimizerConfig config;

	private volatile GearDatabase database;
	private final Map<String, List<ActivityGear>> filterCache = new ConcurrentHashMap<>();
	private final AtomicBoolean updateInProgress = new AtomicBoolean(false);
	private volatile Call activeCall;
	private volatile Future<?> initFuture;

	public void init()
	{
		loadLocalDatabase();
		if (executor != null)
		{
			initFuture = executor.submit(() -> checkRemoteUpdate(false));
		}
	}

	public synchronized void shutdown()
	{
		Future<?> future = this.initFuture;
		this.initFuture = null;
		if (future != null)
		{
			future.cancel(true);
		}

		Call call = this.activeCall;
		this.activeCall = null;
		if (call != null)
		{
			call.cancel();
		}

		filterCache.clear();
		this.database = null;
	}

	public synchronized void loadLocalDatabase()
	{
		filterCache.clear();
		if (CACHE_FILE.exists())
		{
			try (Reader reader = new FileReader(CACHE_FILE, StandardCharsets.UTF_8))
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
		try (InputStream in = getClass().getResourceAsStream("/gear_database.json"))
		{
			if (in != null)
			{
				try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
				{
					GearDatabase loaded = gson.fromJson(reader, GearDatabase.class);
					this.database = loaded;
					if (loaded != null)
					{
						log.info("Loaded bundled gear database from JAR (version: {})", loaded.getVersion());
					}
					return;
				}
			}
		}
		catch (Exception e)
		{
			log.error("Failed to load bundled gear database", e);
		}
		this.database = new GearDatabase(0, "error", Collections.emptyMap());
	}

	public void checkRemoteUpdate()
	{
		checkRemoteUpdate(false);
	}

	public void checkRemoteUpdate(boolean force)
	{
		if (!updateInProgress.compareAndSet(false, true))
		{
			log.debug("Remote gear database update check already in progress; skipping duplicate request.");
			return;
		}

		try
		{
			// Check if auto-sync is disabled by user in configuration
			if (!force && config != null && !config.autoSync())
			{
				log.debug("Auto-sync is disabled in configuration. Skipping remote gear database check.");
				return;
			}

			// Check if 30 days have passed since last check, unless manually forced
			if (!force)
			{
				String lastCheckStr = configManager.getConfiguration(CONFIG_GROUP, KEY_LAST_CHECK);
				if (lastCheckStr != null)
				{
					try
					{
						Instant lastCheck = Instant.parse(lastCheckStr);
						if (Instant.now().isBefore(lastCheck.plus(30, ChronoUnit.DAYS)))
						{
							log.debug("Skipping gear database update check: last check was recent ({})", lastCheck);
							return;
						}
					}
					catch (Exception ignored) {}
				}
			}

			String cachedEtag = configManager.getConfiguration(CONFIG_GROUP, KEY_ETAG);
			Request.Builder requestBuilder = new Request.Builder().url(DEFAULT_REMOTE_URL);

			if (cachedEtag != null && !cachedEtag.isEmpty())
			{
				requestBuilder.header("If-None-Match", cachedEtag);
			}

			Call call = okHttpClient.newCall(requestBuilder.build());
			this.activeCall = call;
			try (Response response = call.execute())
			{
				configManager.setConfiguration(CONFIG_GROUP, KEY_LAST_CHECK, Instant.now().toString());

				if (response.code() == 304)
				{
					log.info("Gear database is already up to date (304 Not Modified)");
					return;
				}

				ResponseBody body = response.body();
				if (response.isSuccessful() && body != null)
				{
					long reportedLength = body.contentLength();
					if (reportedLength > MAX_REMOTE_PAYLOAD_BYTES)
					{
						log.warn("Remote database response Content-Length exceeds maximum allowed size ({} bytes). Aborting update.", reportedLength);
						return;
					}

					byte[] payloadBytes;
					try (InputStream in = body.byteStream())
					{
						payloadBytes = in.readNBytes((int) (MAX_REMOTE_PAYLOAD_BYTES + 1));
					}

					if (payloadBytes.length > MAX_REMOTE_PAYLOAD_BYTES)
					{
						log.warn("Remote database payload stream exceeds maximum allowed size ({} bytes). Aborting update.", MAX_REMOTE_PAYLOAD_BYTES);
						return;
					}

					String json = new String(payloadBytes, StandardCharsets.UTF_8);
					GearDatabase remote = gson.fromJson(json, GearDatabase.class);

					if (remote != null && remote.getVersion() > 0 && remote.getActivities() != null && !remote.getActivities().isEmpty())
					{
						synchronized (this)
						{
							if (database == null || remote.getVersion() > database.getVersion())
							{
								DIRECTORY.mkdirs();
								String validatedJson = gson.toJson(remote);
								Files.writeString(TEMP_CACHE_FILE.toPath(), validatedJson, StandardCharsets.UTF_8);
								Files.move(TEMP_CACHE_FILE.toPath(), CACHE_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
								this.database = remote;
								filterCache.clear();

								String newEtag = response.header("ETag");
								if (newEtag != null)
								{
									configManager.setConfiguration(CONFIG_GROUP, KEY_ETAG, newEtag);
								}

								log.info("Updated gear database to version {}", remote.getVersion());
							}
						}
					}
					else
					{
						log.warn("Remote gear database payload failed structural validation.");
					}
				}
			}
			finally
			{
				this.activeCall = null;
			}
		}
		catch (Exception e)
		{
			log.debug("Unable to perform remote gear database check (operating fully offline): {}", e.getMessage());
		}
		finally
		{
			updateInProgress.set(false);
		}
	}

	public Map<String, ActivityGear> getActivities()
	{
		GearDatabase db = this.database;
		return db != null && db.getActivities() != null ? db.getActivities() : Collections.emptyMap();
	}

	public List<ActivityGear> getAllActivities()
	{
		return getActivities("All Categories", "All Styles");
	}

	public List<ActivityGear> getActivitiesByStyle(String style)
	{
		return getActivities("All Categories", style);
	}

	public List<ActivityGear> getActivities(String category, String style)
	{
		GearDatabase db = this.database;
		if (db == null || db.getActivities() == null)
		{
			return Collections.emptyList();
		}

		String catKey = category == null ? "all" : category.trim().toLowerCase();
		String styleKey = style == null ? "all" : style.trim().toLowerCase();
		String cacheKey = catKey + ":" + styleKey;

		List<ActivityGear> cached = filterCache.get(cacheKey);
		if (cached != null)
		{
			return cached;
		}

		boolean allCategories = catKey.equals("all") || catKey.equals("all categories");
		boolean allStyles = styleKey.equals("all") || styleKey.equals("all styles");

		List<ActivityGear> filtered = new ArrayList<>();
		for (ActivityGear act : db.getActivities().values())
		{
			if (act == null) continue;
			boolean matchCategory = allCategories || (act.getCategory() != null && act.getCategory().equalsIgnoreCase(category));
			boolean matchStyle = allStyles || (act.getStyle() != null && act.getStyle().equalsIgnoreCase(style));

			if (matchCategory && matchStyle)
			{
				filtered.add(act);
			}
		}
		filtered.sort(Comparator.comparing(a -> a.getName() != null ? a.getName() : a.getId()));
		List<ActivityGear> unmodifiable = Collections.unmodifiableList(filtered);

		synchronized (this)
		{
			if (this.database == db)
			{
				filterCache.put(cacheKey, unmodifiable);
			}
		}
		return unmodifiable;
	}

	public ActivityGear getActivity(String id)
	{
		GearDatabase db = this.database;
		return db != null && db.getActivities() != null ? db.getActivities().get(id) : null;
	}

	public long getVersion()
	{
		GearDatabase db = this.database;
		return db != null ? db.getVersion() : 0;
	}
}
