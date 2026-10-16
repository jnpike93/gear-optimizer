package com.gearoptimizer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Singleton
public class PersistentBankCache
{
	private static final File DIRECTORY = new File(RuneLite.RUNELITE_DIR, "gear-optimizer");

	@Inject
	private Gson gson;

	private Set<Integer> cachedBankItemIds = new HashSet<>();
	private long currentAccountHash = -1;

	public synchronized void loadForAccount(long accountHash)
	{
		this.currentAccountHash = accountHash;
		File file = getAccountFile(accountHash);

		if (file.exists())
		{
			try
			{
				String json = Files.readString(file.toPath());
				Set<Integer> loaded = gson.fromJson(json, new TypeToken<HashSet<Integer>>(){}.getType());
				if (loaded != null)
				{
					this.cachedBankItemIds = Collections.synchronizedSet(new HashSet<>(loaded));
					log.info("Loaded {} cached bank items for account {}", cachedBankItemIds.size(), accountHash);
					return;
				}
			}
			catch (Exception e)
			{
				log.warn("Failed to read bank cache file for account {}", accountHash, e);
			}
		}

		this.cachedBankItemIds = Collections.synchronizedSet(new HashSet<>());
	}

	public synchronized void save(Set<Integer> items, long accountHash)
	{
		this.cachedBankItemIds = Collections.synchronizedSet(new HashSet<>(items));
		this.currentAccountHash = accountHash;

		try
		{
			DIRECTORY.mkdirs();
			File file = getAccountFile(accountHash);
			String json = gson.toJson(this.cachedBankItemIds);
			Files.writeString(file.toPath(), json);
			log.debug("Persisted {} bank items for account {}", items.size(), accountHash);
		}
		catch (Exception e)
		{
			log.error("Failed to save bank cache file for account {}", accountHash, e);
		}
	}

	public boolean hasItem(int canonicalId)
	{
		return cachedBankItemIds.contains(canonicalId);
	}

	public int getItemCount()
	{
		return cachedBankItemIds.size();
	}

	public void clear()
	{
		cachedBankItemIds.clear();
		currentAccountHash = -1;
	}

	private File getAccountFile(long accountHash)
	{
		return new File(DIRECTORY, "bank_" + accountHash + ".json");
	}
}

