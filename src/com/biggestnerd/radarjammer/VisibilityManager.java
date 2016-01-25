package com.biggestnerd.radarjammer;

import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class VisibilityManager implements Listener{
	
	private ConcurrentHashMap<Player, PlayerLocation> lastLocations;
	private final int minCheck;
	private final int maxCheck; 

	public VisibilityManager(int minCheck, int maxCheck) {
		lastLocations = new ConcurrentHashMap<Player, PlayerLocation>();
		this.minCheck = minCheck;
		this.maxCheck = maxCheck;
	}
	
	private void updatePlayer(Player player, PlayerLocation loc) {
		lastLocations.put(player, loc);
		updateVisible(player);
	}

	private void updateVisible(Player player) {
		for(Entry<Player, PlayerLocation> entry : lastLocations.entrySet()) {
			if(entry.getKey().equals(player)) continue;
			PlayerLocation pLoc = lastLocations.get(player);
			PlayerLocation oLoc = entry.getValue();
			double dist = pLoc.getDistance(oLoc);
			if(player.hasPermission("jammer.bypass")) {
				player.showPlayer(entry.getKey());
			} else if(dist > minCheck) {
				if(dist < maxCheck && pLoc.canSee(oLoc)) {
					player.showPlayer(entry.getKey());
				} else {
					player.hidePlayer(entry.getKey());
				}
			} else {
				player.showPlayer(entry.getKey());
			}
		}
	}
	
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		updatePlayer(player, new PlayerLocation(player.getLocation()));
	}
	
	@EventHandler
	public void onPlayerLeave(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		lastLocations.remove(player);
	}
	
	@EventHandler
	public void onPlayerMove(PlayerMoveEvent event) {
		Player player = event.getPlayer();
		updatePlayer(player, new PlayerLocation(player.getLocation()));
	}
}