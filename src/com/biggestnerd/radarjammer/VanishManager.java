package com.biggestnerd.radarjammer;

import org.bukkit.entity.Player;
import org.kitteh.vanish.staticaccess.VanishNoPacket;
import org.kitteh.vanish.staticaccess.VanishNotLoadedException;

@SuppressWarnings("deprecation")
public class VanishManager {
	
	public static boolean isVanished(Player player) {
		try {
			return VanishNoPacket.getPlugin().getManager().isVanished(player);
		} catch (VanishNotLoadedException e) {
			return false;
		}
	}
}
