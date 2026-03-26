package com.founderfinder.ac130gunship;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class AC130GunshipPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(AC130GunshipPlugin.class);
		RuneLite.main(args);
	}
}
