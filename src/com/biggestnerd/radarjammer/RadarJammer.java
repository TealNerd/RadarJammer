package com.biggestnerd.radarjammer;

import java.util.HashMap;
import java.util.UUID;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.ProtocolLibrary;

import net.md_5.bungee.api.ChatColor;

public class RadarJammer extends JavaPlugin {
	
	private VisibilityManager visManager;
	private HashMap<UUID, Long> selfieCooldown;
	private long selfieDelay;
	
	@Override
	public void onEnable() {
		selfieCooldown = new HashMap<UUID, Long>();
		saveDefaultConfig();
		reloadConfig();
		initializeVisibilityManager();
		selfieDelay = getConfig().getLong("selfieDelay", 60000);
		ProtocolLibrary.getProtocolManager().addPacketListener(new PlayerListManager(this));
		getCommand("selfie").setExecutor(this);
		CompatManager.initialize();
	}
	
	@Override
	public void onDisable() {
		getLogger().info("RadarJammer shutting down!");
	}
	
	private void initializeVisibilityManager() {
		FileConfiguration config = getConfig();
		int minCheck = config.getInt("minCheck", 10);
		String maxCheckString = config.getString("maxCheck", "auto");
		int maxCheck = 0;
		if(maxCheckString.equals("auto")) {
			maxCheck = (int) (Math.sqrt(2) * ((getServer().getViewDistance() + 1) * 16));
		} else {
			maxCheck = config.getInt("maxCheck");
		}
		double vFov = config.getDouble("vFov", 35.0);
		double hFov = config.getDouble("hFov", 65.0);
		
		boolean showCombatTagged = config.getBoolean("showCombatTagged", true);
		boolean timing = config.getBoolean("timing", false);
		float maxSpin = config.getInt("maxSpin", 500);
		long flagTime = config.getInt("flagTime", 60) * 1000l;
		int maxFlags = config.getInt("maxFlags", 100);
		int blindDuration = config.getInt("blindDuration", 3);
		boolean loadtest = config.getBoolean("loadtest", false);

		long maxLogoutTime = config.getLong("maxLogoutTime", 600000);
		
		boolean entities = config.getBoolean("hideEntities", false);
		boolean suppress = config.getBoolean("suppresslogging", true);
		visManager = new VisibilityManager(this, minCheck, maxCheck, hFov, vFov, showCombatTagged, timing, 
						maxSpin, flagTime, maxFlags, blindDuration, loadtest, maxLogoutTime, entities, suppress);
		getServer().getPluginManager().registerEvents(visManager, this);
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if(!(sender instanceof Player)) {
			sender.sendMessage(ChatColor.RED + "Only players can take selfies!");
			return true;
		}
		Player player = (Player)sender;
		if(!selfieCooldown.containsKey(player.getUniqueId())) {
			selfieCooldown.put(player.getUniqueId(), 0L);
		}
		if(visManager.isInSelfieMode(player)) {
			selfieCooldown.put(player.getUniqueId(), System.currentTimeMillis());
			visManager.toggleSelfieMode(player);
		} else if(System.currentTimeMillis() - selfieCooldown.get(player.getUniqueId()) < selfieDelay) {
			player.sendMessage(ChatColor.RED + "You have to wait before using selfie mode again!");
		} else {
			visManager.toggleSelfieMode(player);
		}
		return true;
	}
}