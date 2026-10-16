package com.gearoptimizer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("gearoptimizer")
public interface GearOptimizerConfig extends Config
{
	@ConfigItem(
		keyName = "autoSync",
		name = "Check for Monthly Updates",
		description = "Automatically check the remote static repository for Wiki gear updates monthly (30 days)",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		position = 1
	)
	default boolean autoSync()
	{
		return false;
	}

	@ConfigItem(
		keyName = "includeInventory",
		name = "Include Inventory Supplies",
		description = "Include potions, food, and runes when building Inventory Setups",
		position = 2
	)
	default boolean includeInventory()
	{
		return true;
	}

	@ConfigItem(
		keyName = "autoCopy",
		name = "Auto-Copy to Clipboard",
		description = "Automatically copy the Inventory Setup JSON to clipboard whenever a setup is generated",
		position = 3
	)
	default boolean autoCopy()
	{
		return true;
	}
}

