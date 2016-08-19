package com.biggestnerd.radarjammer;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.kitteh.vanish.VanishPlugin;

import net.minelink.ctplus.CombatTagPlus;
import net.minelink.ctplus.TagManager;

public class CompatManager {

	private static TagManager ctManager;
	private static VanishPlugin vnp;
	private static boolean combattag = false;
	private static boolean vanish = false;
	
	public static boolean isTagged(UUID player) {
		return combattag && ctManager.isTagged(player);
	}
	
	public static boolean isVanished(Player player) {
		return vanish && vnp.getManager().isVanished(player);
	}
	
	public static void initialize() {
		if(Bukkit.getPluginManager().isPluginEnabled("CombatTagPlus")) {
			ctManager = ((CombatTagPlus)Bukkit.getServer().getPluginManager().getPlugin("CombatTagPlus")).getTagManager();
			combattag = true;
		}
		if(Bukkit.getPluginManager().isPluginEnabled("VanishNoPacket")) {
			vnp = (VanishPlugin) Bukkit.getPluginManager().getPlugin("VanishNoPacket");
			vanish = true;
		}
	}
}
