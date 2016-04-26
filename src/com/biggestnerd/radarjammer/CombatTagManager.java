package com.biggestnerd.radarjammer;

import java.util.UUID;

import org.bukkit.Bukkit;

import net.minelink.ctplus.CombatTagPlus;
import net.minelink.ctplus.TagManager;

public class CombatTagManager {

	private static TagManager ctManager;
	
	public static boolean isTagged(UUID player) {
		return ctManager.isTagged(player);
	}
	
	public static void initialize() {
		if(!Bukkit.getServer().getPluginManager().isPluginEnabled("CombatTagPlus")) return;
		ctManager = ((CombatTagPlus)Bukkit.getServer().getPluginManager().getPlugin("CombatTagPlus")).getTagManager();
	}
}
