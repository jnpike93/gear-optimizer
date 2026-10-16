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
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

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

	private GearOptimizerPanel panel;
	private NavigationButton navButton;

	@Override
	protected void startUp() throws Exception
	{
		log.info("Starting Gear Optimizer plugin...");

		dbManager.init();

		panel = new GearOptimizerPanel(dbManager, optimizer, exporter, bankCache, itemManager);

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

		// If user was already logged in when enabling plugin, load their cache
		clientThread.invokeLater(() -> {
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				bankCache.loadForAccount(client.getAccountHash());
				SwingUtilities.invokeLater(() -> panel.updateBankStatus());
			}
		});
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Shutting down Gear Optimizer plugin...");
		clientToolbar.removeNavigation(navButton);
		bankCache.clear();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			long accountHash = client.getAccountHash();
			bankCache.loadForAccount(accountHash);
			SwingUtilities.invokeLater(() -> panel.updateBankStatus());
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.BANK.getId())
		{
			ItemContainer bank = event.getItemContainer();
			if (bank == null)
			{
				return;
			}

			Set<Integer> ownedItemIds = new HashSet<>();
			for (Item item : bank.getItems())
			{
				if (item != null && item.getId() > 0)
				{
					int canonical = itemManager.canonicalize(item.getId());
					ownedItemIds.add(canonical);
				}
			}

			long accountHash = client.getAccountHash();
			bankCache.save(ownedItemIds, accountHash);

			SwingUtilities.invokeLater(() -> panel.updateBankStatus());
		}
	}

	@Provides
	GearOptimizerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GearOptimizerConfig.class);
	}
}

