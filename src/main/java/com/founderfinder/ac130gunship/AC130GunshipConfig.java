package com.founderfinder.ac130gunship;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("ac130gunship")
public interface AC130GunshipConfig extends Config
{
	@ConfigItem(
		keyName = "ac130Enabled",
		name = "Enabled",
		description = "Allow AC-130 mode activation via Alt key or panel button",
		position = 0
	)
	default boolean ac130Enabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showChatMessages",
		name = "Show Chat Messages",
		description = "Show AC-130 event messages in chat",
		position = 1
	)
	default boolean showChatMessages()
	{
		return true;
	}
}
