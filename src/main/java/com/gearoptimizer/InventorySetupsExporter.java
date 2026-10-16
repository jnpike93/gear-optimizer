package com.gearoptimizer;

import com.gearoptimizer.SetupOptimizer.OptimizationResult;
import com.gearoptimizer.model.InventoryItem;
import com.gearoptimizer.model.InventorySetupPortable;
import com.gearoptimizer.model.InventorySetupPortable.InventorySetupItemSerializable;
import com.gearoptimizer.model.InventorySetupPortable.InventorySetupSerializable;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Singleton
public class InventorySetupsExporter
{
	private static final String INVENTORY_SETUPS_PLUGIN_CLASS = "inventorysetups.InventorySetupsPlugin";
	private static final String CONFIG_GROUP = "inventorysetups";
	private static final String CONFIG_KEY_SETUPS_V3_PREFIX = "setupsV3_";
	private static final String CONFIG_KEY_SETUPS_ORDER_V3 = "setupsOrderV3_";

	@Inject
	private Gson gson;

	@Inject
	private ConfigManager configManager;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private GearOptimizerConfig config;

	public InventorySetupPortable createPortableSetup(OptimizationResult result)
	{
		if (result == null || result.getActivity() == null)
		{
			return null;
		}

		// 14 equipment slots: 0=HEAD, 1=CAPE, 2=AMULET, 3=WEAPON, 4=BODY, 5=SHIELD,
		// 6=ARMS (null), 7=LEGS, 8=HAIR (null), 9=GLOVES, 10=BOOTS, 11=JAW (null), 12=RING, 13=AMMO
		List<InventorySetupItemSerializable> eq = new ArrayList<>(Collections.nCopies(14, null));
		Integer iconId = null;

		for (Map.Entry<String, Integer> entry : result.getEquippedItems().entrySet())
		{
			int slotIdx = getEquipmentSlotIndex(entry.getKey());
			if (slotIdx >= 0 && slotIdx < 14)
			{
				eq.set(slotIdx, new InventorySetupItemSerializable(entry.getValue()));
				if (slotIdx == 3) // Weapon as preferred icon
				{
					iconId = entry.getValue();
				}
				else if (iconId == null && slotIdx == 0) // Head as fallback icon
				{
					iconId = entry.getValue();
				}
			}
		}

		// 28 inventory slots
		List<InventorySetupItemSerializable> inv = new ArrayList<>(Collections.nCopies(28, null));
		int invIdx = 0;
		if (result.getAvailableInventory() != null)
		{
			for (InventoryItem invItem : result.getAvailableInventory())
			{
				if (invIdx >= 28)
				{
					break;
				}
				inv.set(invIdx++, new InventorySetupItemSerializable(invItem.getId(), invItem.getQty()));
			}
		}

		InventorySetupSerializable setup = InventorySetupSerializable.builder()
			.name(result.getActivity().getName())
			.notes("Optimized by RuneLite Gear Optimizer from OSRS Wiki recommendations")
			.eq(eq)
			.inv(inv)
			.afi(new HashMap<>())
			.fb(Boolean.TRUE) // Filter bank when setup is selected
			.hd(Boolean.FALSE)
			.uh(Boolean.FALSE)
			.fv(Boolean.FALSE)
			.sb(0)
			.iId(iconId)
			.ao("")
			.build();

		return InventorySetupPortable.builder()
			.setup(setup)
			.layout(new int[0])
			.build();
	}

	public String exportToInventorySetupJson(OptimizationResult result)
	{
		InventorySetupPortable portable = createPortableSetup(result);
		return portable != null ? gson.toJson(portable) : null;
	}

	public boolean copySetupToClipboard(OptimizationResult result)
	{
		String json = exportToInventorySetupJson(result);
		if (json == null)
		{
			return false;
		}

		try
		{
			StringSelection selection = new StringSelection(json);
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			clipboard.setContents(selection, selection);
			log.info("Exported portable setup for '{}' to clipboard", result.getActivity().getName());
			return true;
		}
		catch (Exception e)
		{
			log.error("Failed to copy setup to clipboard", e);
			return false;
		}
	}

	/**
	 * Directly interfaces with the Inventory Setups plugin:
	 * 1. If InventorySetupsPlugin is actively running in RuneLite, injects the setup
	 *    into the running instance via reflection, updating cache, layouts, config, and UI.
	 * 2. If not running, persists directly into RuneLite's ConfigManager under the inventorysetups group.
	 * 3. Only copies to clipboard if user has autoCopy enabled in configuration.
	 */
	public boolean directImportToInventorySetups(OptimizationResult result)
	{
		if (result == null || result.getActivity() == null)
		{
			return false;
		}

		// Honor user configuration: only copy to clipboard if autoCopy is enabled
		if (config != null && config.autoCopy())
		{
			copySetupToClipboard(result);
		}

		InventorySetupPortable portable = createPortableSetup(result);
		String json = gson.toJson(portable);

		// Check for running InventorySetupsPlugin
		Plugin invSetupsPlugin = findInventorySetupsPlugin();

		if (invSetupsPlugin != null)
		{
			boolean success = injectLiveSetup(invSetupsPlugin, json);
			if (success)
			{
				log.info("Directly injected setup '{}' into active Inventory Setups plugin", result.getActivity().getName());
				return true;
			}
		}

		// Fallback: Persist directly into ConfigManager
		return persistToConfigManager(portable);
	}

	public boolean isInventorySetupsInstalled()
	{
		return findInventorySetupsPlugin() != null;
	}

	private Plugin findInventorySetupsPlugin()
	{
		if (pluginManager == null)
		{
			return null;
		}

		for (Plugin p : pluginManager.getPlugins())
		{
			if (INVENTORY_SETUPS_PLUGIN_CLASS.equals(p.getClass().getName()))
			{
				return p;
			}
		}
		return null;
	}

	private boolean injectLiveSetup(Plugin plugin, String portableJson)
	{
		try
		{
			ClassLoader cl = plugin.getClass().getClassLoader();
			Class<?> portableClass = cl.loadClass("inventorysetups.serialization.InventorySetupPortable");

			Field gsonField = plugin.getClass().getDeclaredField("gson");
			gsonField.setAccessible(true);
			Gson pluginGson = (Gson) gsonField.get(plugin);

			Object portableObj = pluginGson.fromJson(portableJson, portableClass);
			Method getSerializedSetup = portableClass.getMethod("getSerializedSetup");
			Object newSetup = getSerializedSetup.invoke(portableObj);

			if (newSetup == null)
			{
				return false;
			}

			clientThread.invokeLater(() -> {
				try
				{
					Method preProcess = plugin.getClass().getDeclaredMethod("preProcessNewSetup", newSetup.getClass());
					preProcess.setAccessible(true);
					preProcess.invoke(plugin, newSetup);

					Method getCache = plugin.getClass().getMethod("getCache");
					Object cache = getCache.invoke(plugin);
					if (cache != null)
					{
						Method cacheAdd = cache.getClass().getMethod("addSetup", newSetup.getClass());
						cacheAdd.invoke(cache, newSetup);
					}

					Method getSetups = plugin.getClass().getMethod("getInventorySetups");
					@SuppressWarnings("unchecked")
					List<Object> setupsList = (List<Object>) getSetups.invoke(plugin);
					if (setupsList != null)
					{
						// Deduplicate setup by name before appending
						try
						{
							Method getNameMethod = newSetup.getClass().getMethod("getName");
							String newName = (String) getNameMethod.invoke(newSetup);
							if (newName != null)
							{
								setupsList.removeIf(existing -> {
									try
									{
										return newName.equalsIgnoreCase((String) existing.getClass().getMethod("getName").invoke(existing));
									}
									catch (Exception ignored)
									{
										return false;
									}
								});
							}
						}
						catch (Exception ignored) {}

						setupsList.add(newSetup);
					}

					Method getLayoutUtils = plugin.getClass().getMethod("getLayoutUtilities");
					Object layoutUtils = getLayoutUtils.invoke(plugin);
					if (layoutUtils != null)
					{
						Method createSetupLayout = layoutUtils.getClass().getMethod("createSetupLayout", newSetup.getClass());
						Object tempLayout = createSetupLayout.invoke(layoutUtils, newSetup);

						if (tempLayout != null)
						{
							Method getLayout = portableClass.getMethod("getLayout");
							int[] layoutArr = (int[]) getLayout.invoke(portableObj);

							Method getTag = tempLayout.getClass().getMethod("getTag");
							String tag = (String) getTag.invoke(tempLayout);

							Constructor<?> layoutCtor = tempLayout.getClass().getConstructor(String.class, int[].class);
							Object newLayout = layoutCtor.newInstance(tag, layoutArr);

							Method getLayoutManager = plugin.getClass().getMethod("getLayoutManager");
							Object layoutManager = getLayoutManager.invoke(plugin);
							if (layoutManager != null)
							{
								Method saveLayout = layoutManager.getClass().getMethod("saveLayout", tempLayout.getClass());
								saveLayout.invoke(layoutManager, newLayout);
							}

							Method getTagManager = plugin.getClass().getMethod("getTagManager");
							Object tagManager = getTagManager.invoke(plugin);
							if (tagManager != null)
							{
								Method setHidden = tagManager.getClass().getMethod("setHidden", String.class, boolean.class);
								setHidden.invoke(tagManager, tag, true);
							}
						}
					}

					Method getDataManager = plugin.getClass().getMethod("getDataManager");
					Object dataManager = getDataManager.invoke(plugin);
					if (dataManager != null)
					{
						Method updateConfig = dataManager.getClass().getMethod("updateConfig", boolean.class, boolean.class);
						updateConfig.invoke(dataManager, true, false);
					}

					Field panelField = plugin.getClass().getDeclaredField("panel");
					panelField.setAccessible(true);
					Object panelObj = panelField.get(plugin);
					if (panelObj != null)
					{
						Method redraw = panelObj.getClass().getMethod("redrawOverviewPanel", boolean.class);
						SwingUtilities.invokeLater(() -> {
							try
							{
								redraw.invoke(panelObj, false);
							}
							catch (Exception ignored) {}
						});
					}
				}
				catch (Exception e)
				{
					log.error("Failed to add live setup in InventorySetupsPlugin", e);
				}
			});

			return true;
		}
		catch (Exception e)
		{
			log.warn("Direct live injection into InventorySetupsPlugin failed", e);
			return false;
		}
	}

	private boolean persistToConfigManager(InventorySetupPortable portable)
	{
		try
		{
			String setupName = portable.getSetup().getName();
			String hash = Hashing.murmur3_128().hashUnencodedChars(setupName).toString();
			String serializableJson = gson.toJson(portable.getSetup());

			configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_SETUPS_V3_PREFIX + hash, serializableJson);

			String orderJson = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_SETUPS_ORDER_V3);
			List<String> order = orderJson != null
				? gson.fromJson(orderJson, new TypeToken<ArrayList<String>>(){}.getType())
				: new ArrayList<>();

			if (order == null)
			{
				order = new ArrayList<>();
			}

			if (!order.contains(hash))
			{
				order.add(hash);
				configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_SETUPS_ORDER_V3, gson.toJson(order));
			}

			log.info("Persisted setup '{}' directly to configManager ({}{})", setupName, CONFIG_KEY_SETUPS_V3_PREFIX, hash);
			return true;
		}
		catch (Exception e)
		{
			log.error("Failed to persist setup to configManager", e);
			return false;
		}
	}

	private int getEquipmentSlotIndex(String slot)
	{
		if (slot == null) return -1;
		switch (slot.toLowerCase().trim())
		{
			case "head":
			case "helm":
			case "helmet":
				return 0;
			case "cape":
			case "cloak":
				return 1;
			case "neck":
			case "necklace":
			case "amulet":
				return 2;
			case "weapon":
			case "mainhand":
				return 3;
			case "body":
			case "torso":
			case "chest":
			case "platebody":
				return 4;
			case "shield":
			case "offhand":
			case "defender":
				return 5;
			case "legs":
			case "platelegs":
			case "tassets":
				return 7;
			case "hands":
			case "gloves":
			case "vambraces":
				return 9;
			case "boots":
			case "feet":
				return 10;
			case "ring":
				return 12;
			case "ammo":
			case "ammunition":
			case "arrows":
			case "bolts":
				return 13;
			default:
				return -1;
		}
	}
}
