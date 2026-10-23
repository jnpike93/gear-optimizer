package com.gearoptimizer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Singleton
public class PersistentBankCache
{
	private static final File DIRECTORY = new File(RuneLite.RUNELITE_DIR, "gear-optimizer");

	@Inject
	private Gson gson;

	@Inject
	private ScheduledExecutorService executor;

	private final Set<Integer> cachedBankItemIds = new HashSet<>();
	private final Object ioLock = new Object();
	private final AtomicLong saveGeneration = new AtomicLong(0);
	private volatile long currentAccountHash = -1;

	public void loadForAccount(long accountHash)
	{
		this.currentAccountHash = accountHash;
		File file = getAccountFile(accountHash);

		synchronized (ioLock)
		{
			if (file.exists())
			{
				try
				{
					String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
					Set<Integer> loaded = gson.fromJson(json, new TypeToken<HashSet<Integer>>(){}.getType());
					if (loaded != null)
					{
						synchronized (cachedBankItemIds)
						{
							cachedBankItemIds.clear();
							cachedBankItemIds.addAll(loaded);
						}
						log.info("Loaded {} cached bank items for account ending in {}",
							loaded.size(), getMaskedHash(accountHash));
						return;
					}
				}
				catch (Exception e)
				{
					log.warn("Failed to read bank cache file for account ending in {}", getMaskedHash(accountHash), e);
				}
			}
		}

		synchronized (cachedBankItemIds)
		{
			cachedBankItemIds.clear();
		}
	}

	public void updateInMemory(Set<Integer> items)
	{
		synchronized (cachedBankItemIds)
		{
			cachedBankItemIds.clear();
			cachedBankItemIds.addAll(items);
		}
	}

	public void save(Set<Integer> items, long accountHash)
	{
		updateInMemory(items);
		this.currentAccountHash = accountHash;

		final long generation = saveGeneration.incrementAndGet();
		final Set<Integer> snapshot = new HashSet<>(items);
		final String masked = getMaskedHash(accountHash);

		Runnable saveTask = () -> {
			synchronized (ioLock)
			{
				// Skip stale write if a newer snapshot was already queued
				if (generation < saveGeneration.get())
				{
					return;
				}

				try
				{
					DIRECTORY.mkdirs();
					File file = getAccountFile(accountHash);
					File tempFile = new File(DIRECTORY, "bank_" + accountHash + ".json.tmp");
					String json = gson.toJson(snapshot);
					Files.writeString(tempFile.toPath(), json, StandardCharsets.UTF_8);
					Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);

					// Restrict permissions to owner only
					file.setReadable(false, false);
					file.setReadable(true, true);
					file.setWritable(false, false);
					file.setWritable(true, true);
					file.setExecutable(false, false);

					log.debug("Persisted {} bank items for account ending in {}", snapshot.size(), masked);
				}
				catch (Exception e)
				{
					log.error("Failed to save bank cache file for account ending in {}", masked, e);
				}
			}
		};

		if (executor != null)
		{
			executor.execute(saveTask);
		}
		else
		{
			saveTask.run();
		}
	}

	public boolean hasItem(int canonicalId)
	{
		synchronized (cachedBankItemIds)
		{
			return cachedBankItemIds.contains(canonicalId);
		}
	}

	public Set<Integer> getCachedItemIds()
	{
		synchronized (cachedBankItemIds)
		{
			return new HashSet<>(cachedBankItemIds);
		}
	}

	public int getItemCount()
	{
		synchronized (cachedBankItemIds)
		{
			return cachedBankItemIds.size();
		}
	}

	public void clear()
	{
		synchronized (cachedBankItemIds)
		{
			cachedBankItemIds.clear();
		}
		currentAccountHash = -1;
	}

	public long getCurrentAccountHash()
	{
		return currentAccountHash;
	}

	private String getMaskedHash(long hash)
	{
		return String.format("%04x", (hash & 0xFFFF));
	}

	private File getAccountFile(long accountHash)
	{
		return new File(DIRECTORY, "bank_" + accountHash + ".json");
	}
}
