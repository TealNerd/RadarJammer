package com.biggestnerd.radarjammer;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import net.minelink.ctplus.CombatTagPlus;
import net.minelink.ctplus.TagManager;

public class VisibilityManager implements Listener, Runnable{
	
	private final int minCheck;
	private final int maxCheck;
	private final double maxFov;
	private final boolean showCombatTagged;
	
	private Set<PlayerLocation> lastLocations;
	private ConcurrentHashMap<UUID, HashSet<UUID>> hiddenMap;
	private ConcurrentHashMap<UUID, HashSet<UUID>> toShow;
	private ConcurrentHashMap<UUID, HashSet<UUID>> toHide;
	
	private VisibilityThread visThread;
	private TagManager ctManager;
	private Logger log;
	
	public VisibilityManager(RadarJammer plugin, int minCheck, int maxCheck, double maxFov, boolean showCombatTagged) {
		log = plugin.getLogger();
		lastLocations = Collections.synchronizedSet(new HashSet<PlayerLocation>());
		hiddenMap = new ConcurrentHashMap<UUID,	HashSet<UUID>>();
		toShow = new ConcurrentHashMap<UUID, HashSet<UUID>>();
		toHide = new ConcurrentHashMap<UUID, HashSet<UUID>>();
		this.minCheck = minCheck;
		this.maxCheck = maxCheck;
		this.maxFov = maxFov;
		boolean ctEnabled = plugin.getServer().getPluginManager().isPluginEnabled("CombatTagPlus");
		if(!ctEnabled || !showCombatTagged) {
			this.showCombatTagged = false;
		} else {
			log.info("RadarJammer will show combat tagged players.");
			this.showCombatTagged = true;
			ctManager = ((CombatTagPlus) plugin.getServer().getPluginManager().getPlugin("CombatTagPlus")).getTagManager();
		}
		Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this, 0L, 1L);
		visThread = new VisibilityThread();
		visThread.start();
		log.info(String.format("VisibilityManager initialized! minCheck: %d, maxCheck: %d, maxFov: %f, showCombatTagged: %b", minCheck, maxCheck, maxFov, this.showCombatTagged));
	}
	
	@Override
	public void run() {
		for(Player p : Bukkit.getOnlinePlayers()) {
			HashSet<UUID> show = toShow.get(p.getUniqueId());
			if(show.size() != 0) {
				for(UUID id : show) {
					Player o = Bukkit.getPlayer(id);
					if(o != null) {
						log.info(String.format("Showing %s to %s", o.getName(), p.getName()));
						p.showPlayer(o);
					}
				}
			}
			toShow.get(p.getUniqueId()).clear();
			if(p.hasPermission("jammer.bypass")) continue;
			HashSet<UUID> hide = toHide.get(p.getUniqueId());
			if(hide.size() != 0) {
				for(UUID id : hide) {
					Player o = Bukkit.getPlayer(id);
					if(o != null) {
						log.info(String.format("Hiding %s from %s", o.getName(), p.getName()));
						if(showCombatTagged && ctManager.isTagged(id)) continue;
						p.hidePlayer(o);
					}
				}
			}
			toHide.get(p.getUniqueId()).clear();
		}
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		lastLocations.add(new PlayerLocation(player.getEyeLocation(), player.getUniqueId()));
		hiddenMap.put(player.getUniqueId(), new HashSet<UUID>());
		toShow.put(player.getUniqueId(), new HashSet<UUID>());
		toHide.put(player.getUniqueId(), new HashSet<UUID>());
	}
	
	@EventHandler
	public void onPlayerLeave(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		lastLocations.remove(player);
		hiddenMap.remove(player.getUniqueId());
		toShow.remove(player.getUniqueId());
		toHide.remove(player.getUniqueId());
	}
	
	@EventHandler
	public void onPlayerMove(PlayerMoveEvent event) {
		Player player = event.getPlayer();
		lastLocations.add(new PlayerLocation(player.getEyeLocation(), player.getUniqueId()));
	}
	
	class VisibilityThread extends Thread {
		
		private long lastRun = 0;
		
		public void run() {
			log.info("RadarJammer: Starting calculation thread!");
			while(true) {
				delay();
				calculate();
			}
		}

		private void calculate() {
			HashMap<UUID, HashSet<UUID>> checked = new HashMap<UUID, HashSet<UUID>>();
			HashSet<PlayerLocation> locations = new HashSet<PlayerLocation>();
			synchronized(lastLocations) {
				locations.addAll(lastLocations);
			}
			for(PlayerLocation loc : locations) {
				UUID id = loc.getID();
				if(!checked.containsKey(id)) checked.put(id, new HashSet<UUID>());
				for(PlayerLocation oloc : locations) {
					UUID oid = oloc.getID();
					if(!checked.containsKey(oid)) checked.put(oid, new HashSet<UUID>());
					if(checked.get(id).contains(oid) || id.equals(oid)) continue;
					boolean hideOther;
					boolean hidePlayer;
					double dist = loc.getDistance(oloc);
					if(dist > minCheck) {
						if(dist < maxCheck) {
							hideOther = loc.getAngle(oloc) > maxFov;
							hidePlayer = oloc.getAngle(loc) > maxFov;
						} else {
							hideOther = hidePlayer = true;
						}
					} else {
						hideOther = hidePlayer = false;
					}
					boolean hidingOther = hiddenMap.get(id).contains(oid);
					if(hidingOther != hideOther) {
						if(hideOther) {
							toHide.get(id).add(oid);
							hiddenMap.get(id).add(oid);
						} else {
							toShow.get(id).add(oid);
							hiddenMap.get(id).remove(oid);
						}
					}
					boolean hidingPlayer = hiddenMap.get(oid).contains(id);
					if(hidingPlayer != hidePlayer) {
						if(hidePlayer) {
							toHide.get(oid).add(id);
							hiddenMap.get(oid).add(id);
						} else {
							toShow.get(oid).add(id);
							hiddenMap.get(oid).remove(id);
						}
					}
					checked.get(id).add(oid);
					checked.get(oid).add(id);
				}
			}
		}
		
		private void delay() {
			long time = System.currentTimeMillis() - lastRun;
			if(time < 100L) {
				try {
					sleep(100L - time);
				} catch (InterruptedException e) {e.printStackTrace();}
			}
		}
	}
}