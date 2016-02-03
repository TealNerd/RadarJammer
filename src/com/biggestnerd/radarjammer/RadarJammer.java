package com.biggestnerd.radarjammer;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class RadarJammer extends JavaPlugin {
	
	private VisibilityManager visManager;
	
	@Override
	public void onEnable() {
		saveDefaultConfig();
		reloadConfig();
		registerVisibilityManager();
	}
	
	@Override
	public void onDisable() {
		getLogger().info("RadarJammer shutting down!");
	}
	
	private void registerVisibilityManager() {
		FileConfiguration config = getConfig();
		int minCheck = config.getInt("minCheck", 14);
		String maxCheckString = config.getString("maxCheck", "auto");
		int maxCheck = 0;
		if(maxCheckString.equals("auto")) {
			maxCheck = getServer().getViewDistance() * 16 + 8;
		} else {
			maxCheck = config.getInt("maxCheck");
		}
		double vFov = config.getDouble("vFov", 35.0);
		double hFov = config.getDouble("hFov", 60.0);
		double maxFov = Math.sqrt((vFov * vFov) + (hFov * hFov));
		
		boolean showCombatTagged = config.getBoolean("showCombatTagged", false);
		
		visManager = new VisibilityManager(this, minCheck, maxCheck, maxFov, showCombatTagged);
		getServer().getPluginManager().registerEvents(visManager, this);
	}
}