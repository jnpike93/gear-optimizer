package com.gearoptimizer;

import com.gearoptimizer.ui.GearOptimizerPanel;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@PluginDescriptor(
	name = "Gear Optimizer",
	description = "Recommends optimal gear setups from your bank based on OSRS Wiki guides and exports to Inventory Setups",
	tags = {"gear", "inventory", "setups", "wiki", "optimizer", "bank", "bossing"}
)
public class GearOptimizerPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private GearDatabaseManager dbManager;

	@Inject
	private PersistentBankCache bankCache;

	@Inject
	private SetupOptimizer optimizer;

	@Inject
	private InventorySetupsExporter exporter;

	@Inject
	private GearOptimizerConfig config;

	@Inject
	private ScheduledExecutorService executor;

	private GearOptimizerPanel panel;
	private NavigationButton navButton;

	private final Set<Integer> bankItemIds = new HashSet<>();
	private final Set<Integer> invItemIds = new HashSet<>();
	private final Set<Integer> equipItemIds = new HashSet<>();

	@Override
	protected void startUp() throws Exception
	{
		log.info("Starting Gear Optimizer plugin...");

		dbManager.init();

		panel = new GearOptimizerPanel(dbManager, optimizer, exporter, bankCache, itemManager, clientThread, executor);

		// Default placeholder icon (16x16 shield/sword)
		BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int x = 2; x < 14; x++)
		{
			for (int y = 2; y < 14; y++)
			{
				icon.setRGB(x, y, 0xFFE0B034); // Gold square icon
			}
		}

		navButton = NavigationButton.builder()
			.tooltip("Gear Optimizer")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);

		// If user was already logged in when enabling plugin, load their cache and scan items
		clientThread.invokeLater(() -> {
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				long accountHash = client.getAccountHash();
				bankCache.loadForAccount(accountHash);
				bankItemIds.clear();
				bankItemIds.addAll(bankCache.getCachedItemIds());
				updateOwnedItems(false);
			}
		});
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Shutting down Gear Optimizer plugin...");
		clientToolbar.removeNavigation(navButton);
		bankCache.clear();
		bankItemIds.clear();
		invItemIds.clear();
		equipItemIds.clear();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			long accountHash = client.getAccountHash();
			bankCache.loadForAccount(accountHash);
			bankItemIds.clear();
			bankItemIds.addAll(bankCache.getCachedItemIds());
			clientThread.invokeLater(() -> updateOwnedItems(false));
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int containerId = event.getContainerId();
		boolean isBank = (containerId == InventoryID.BANK.getId());
		boolean isInvOrEquip = (containerId == InventoryID.INVENTORY.getId() || containerId == InventoryID.EQUIPMENT.getId());

		if (isBank)
		{
			updateOwnedItems(true); // Persist bank snapshot asynchronously
		}
		else if (isInvOrEquip)
		{
			updateOwnedItems(false); // In-memory update only, zero disk I/O on game ticks
		}
	}

	private void updateOwnedItems(boolean persistToDisk)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		long accountHash = client.getAccountHash();

		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (bank != null)
		{
			bankItemIds.clear();
			for (Item item : bank.getItems())
			{
				if (item != null && item.getId() > 0)
				{
					addItemAndVariations(bankItemIds, item.getId());
				}
			}
		}

		ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
		if (inv != null)
		{
			invItemIds.clear();
			for (Item item : inv.getItems())
			{
				if (item != null && item.getId() > 0)
				{
					addItemAndVariations(invItemIds, item.getId());
				}
			}
		}

		ItemContainer equip = client.getItemContainer(InventoryID.EQUIPMENT);
		if (equip != null)
		{
			equipItemIds.clear();
			for (Item item : equip.getItems())
			{
				if (item != null && item.getId() > 0)
				{
					addItemAndVariations(equipItemIds, item.getId());
				}
			}
		}

		Set<Integer> allOwned = new HashSet<>();
		allOwned.addAll(bankItemIds);
		allOwned.addAll(invItemIds);
		allOwned.addAll(equipItemIds);

		if (persistToDisk)
		{
			bankCache.save(allOwned, accountHash);
		}
		else
		{
			bankCache.updateInMemory(allOwned);
		}

		SwingUtilities.invokeLater(() -> panel.updateBankStatus());
	}

	private void addItemAndVariations(Set<Integer> targetSet, int itemId)
	{
		targetSet.add(itemId);

		int canonical = itemManager != null ? itemManager.canonicalize(itemId) : itemId;
		targetSet.add(canonical);

		try
		{
			int mappedBase = ItemVariationMapping.map(canonical);
			targetSet.add(mappedBase);

			Collection<Integer> variations = ItemVariationMapping.getVariations(mappedBase);
			if (variations != null && !variations.isEmpty())
			{
				targetSet.addAll(variations);
			}

			int rawMapped = ItemVariationMapping.map(itemId);
			if (rawMapped != mappedBase)
			{
				targetSet.add(rawMapped);
				Collection<Integer> rawVars = ItemVariationMapping.getVariations(rawMapped);
				if (rawVars != null && !rawVars.isEmpty())
				{
					targetSet.addAll(rawVars);
				}
			}
		}
		catch (Exception ignored)
		{
			// Fallback if ItemVariationMapping fails
		}
	}

	@Provides
	GearOptimizerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GearOptimizerConfig.class);
	}
}
