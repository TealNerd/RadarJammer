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
		int minCheck = config.getInt("minCheck", 5);
		String maxCheckString = config.getString("maxCheck", "auto");
		int maxCheck = 0;
		if(maxCheckString.equals("auto")) {
			maxCheck = getServer().getViewDistance() * 16 + 8;
		} else {
			maxCheck = config.getInt("maxCheck");
		}
		PlayerLocation.vFov = config.getDouble("vFov", 30.0);
		PlayerLocation.hFov = config.getDouble("hFov", 50.0);
		visManager = new VisibilityManager(minCheck, maxCheck);
		getServer().getPluginManager().registerEvents(visManager, this);
	}
}