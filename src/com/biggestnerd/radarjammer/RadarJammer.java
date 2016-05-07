package com.biggestnerd.radarjammer;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.ProtocolLibrary;

public class RadarJammer extends JavaPlugin {
	
	private VisibilityManager visManager;
	
	@Override
	public void onEnable() {
		saveDefaultConfig();
		reloadConfig();
		initializeVisibilityManager();
		ProtocolLibrary.getProtocolManager().addPacketListener(new PlayerListManager(this));
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
			maxCheck = getServer().getViewDistance() * 16 + 8;
		} else {
			maxCheck = config.getInt("maxCheck");
		}
		double vFov = config.getDouble("vFov", 35.0);
		double hFov = config.getDouble("hFov", 60.0);
		
		boolean showCombatTagged = config.getBoolean("showCombatTagged", true);
		boolean timing = config.getBoolean("timing", false);
		float maxSpin = config.getInt("maxSpin", 500);
		long flagTime = config.getInt("flagTime", 60) * 1000l;
		int maxFlags = config.getInt("maxFlags", 100);
		int blindDuration = config.getInt("blindDuration", 3);
		boolean loadtest = config.getBoolean("loadtest", false);
		
		visManager = new VisibilityManager(this, minCheck, maxCheck, hFov, vFov, showCombatTagged, timing, maxSpin, flagTime, maxFlags, blindDuration, loadtest);
		getServer().getPluginManager().registerEvents(visManager, this);
	}
}