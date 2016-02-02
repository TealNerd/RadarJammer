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
import org.bukkit.scheduler.BukkitRunnable;

import net.minelink.ctplus.CombatTagPlus;
import net.minelink.ctplus.TagManager;

public class VisibilityManager extends BukkitRunnable implements Listener{
	
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
		runTaskTimer(plugin, 0L, 0L);
		visThread = new VisibilityThread();
		visThread.start();
		log.info(String.format("VisibilityManager initialized! minCheck: %d, maxCheck: %d, maxFov: %f, showCombatTagged: %b", minCheck, maxCheck, maxFov, this.showCombatTagged));
	}

	private long lastCheckRun = 0l;
	
	@Override
	public void run() {
		long s = System.currentTimeMillis();
		long b = 0l;
		long t = 0l;
		long pl = 0l;
		long sh = 0l;
		long hi = 0l;
		double aqp = 0.0d;
		for(Player p : Bukkit.getOnlinePlayers()) {
			b = System.currentTimeMillis();
			pl++;
			HashSet<UUID> show = toShow.get(p.getUniqueId());
			if(show.size() != 0) {
				for(UUID id : show) {
					Player o = Bukkit.getPlayer(id);
					sh++;
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
					hi++;
					if(o != null) {
						log.info(String.format("Hiding %s from %s", o.getName(), p.getName()));
						if(showCombatTagged && ctManager.isTagged(id)) continue;
						p.hidePlayer(o);
					}
				}
			}
			toHide.get(p.getUniqueId()).clear();
			t = System.currentTimeMillis();
			aqp = aqp + ((double)(t-b) - aqp)/pl;
		}
		if ((s - lastCheckRun) > 1000l) {
			if (pl > 0) 
				log.info(String.format("Updated %d players in %d milliseconds, spending %.2f per player. Total %d seen updates and %d hide updates", pl, (t-s), aqp, sh, hi));
			else
				log.info("No players currently tracked.");
			lastCheckRun = s;
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
				lastRun = System.currentTimeMillis();
				calculate();
				if (calcRuns > 20) {
					showAvg();
				}
			}
		}

		private void showAvg() {
			if (calcRuns == 0d || calcPerPlayerRuns == 0d) {
				log.info("RadarJammer Calculations Performance: none done.");
			} else {
				log.info(String.format("RadarJammer Calculations Performance: %.0f runs, %.0f players calculated for.", calcRuns, calcPerPlayerRuns));
				log.info(String.format("    on average: %.5fms in setup, %.4fms per player per run, %.4fms per run", (calcSetupAverage / calcRuns), (calcPerPlayerAverage / calcPerPlayerRuns), (calcAverage / calcRuns)));
			}
			calcSetupAverage = 0d;
			calcPerPlayerAverage = 0d;
			calcPerPlayerRuns = 0d;
			calcAverage =0d;
			calcRuns = 0d;
		}

		private double calcSetupAverage = 0d;
		private double calcPerPlayerAverage = 0d;
		private double calcPerPlayerRuns = 0d;
		private double calcAverage = 0d;
		private double calcRuns = 0d;

		private void calculate() {
			calcRuns ++;
			long s_ = System.currentTimeMillis();
			long b_ = 0l;
			long d_ = 0l;
			long e_ = 0l;

			HashMap<UUID, HashSet<UUID>> checked = new HashMap<UUID, HashSet<UUID>>();
			HashSet<PlayerLocation> locations = new HashSet<PlayerLocation>();
			synchronized(lastLocations) {
				locations.addAll(lastLocations);
			}
			e_ = System.currentTimeMillis();
			calcSetupAverage += (double) (e_ - s_);
			for(PlayerLocation loc : locations) {
				calcPerPlayerRuns++;
				b_ = System.currentTimeMillis();
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
				e_ = System.currentTimeMillis();
				calcPerPlayerAverage += (e_ - b_);
			}
			calcAverage += (e_ - s_);
		}
		
		private void delay() {
			long time = System.currentTimeMillis() - lastRun;
			if(time < 100L && time > -1l) {
				try {
					sleep(100L - time);
				} catch (InterruptedException e) {e.printStackTrace();}
			}
		}
	}
}
