package com.biggestnerd.radarjammer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
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
	private ConcurrentHashMap<UUID, HashSet<UUID>[]> maps;
	private AtomicBoolean buffer;
	
	/*hidden;
	private ConcurrentHashMap<UUID, HashSet<UUID>> toShow;
	private ConcurrentHashMap<UUID, HashSet<UUID>> toHide;*/
	
	private VisibilityThread visThread;
	private TagManager ctManager;
	private Logger log;
	
	public VisibilityManager(RadarJammer plugin, int minCheck, int maxCheck, double maxFov, boolean showCombatTagged) {
		log = plugin.getLogger();
		lastLocations = Collections.synchronizedSet(new HashSet<PlayerLocation>());
		maps = new ConcurrentHashMap<UUID, HashSet<UUID>[]>();
		buffer = new AtomicBoolean();

		/*hiddenMap = new ConcurrentHashMap<UUID,	HashSet<UUID>>();
		toShow = new ConcurrentHashMap<UUID, HashSet<UUID>>();
		toHide = new ConcurrentHashMap<UUID, HashSet<UUID>>();*/
		this.minCheck = minCheck*minCheck;
		this.maxCheck = maxCheck*maxCheck;
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
	
	enum btype {
		SHOW,
		HIDE
	}

	public int getBuffer(btype buf) {
		if (buffer.get()) {
			switch(buf) {
			case SHOW:
				return 1;
			case HIDE:
				return 3;
			}
		} else {
			switch(buf) {
			case SHOW:
				return 2;
			case HIDE:
				return 4;
			}
		}
		return -1;
	}

	@Override
	public void run() {
		long s = System.currentTimeMillis();
		long b = 0l;
		long t = 0l;
		long pl = 0l;
		long sh = 0l;
		long hi = 0l;
		double aqp = 0.0d;
		boolean buff = false;
		// Flip buffers.
		synchronized(buffer) {
			buff = buffer.get();
			buffer.set(!buff);
		}

		for(Player p : Bukkit.getOnlinePlayers()) {
			b = System.currentTimeMillis();
			pl++;
			// now get the set of arrays. Careful to only deal with the "locked" ones
			// based on the semaphore. In this case, "true" gives low-order buffers.
			UUID pu = p.getUniqueId();
			HashSet<UUID>[] buffers = maps.get(pu);
			if (buffers == null) {
				maps.put(pu, allocate());
			}

			HashSet<UUID> show = buffers[buff?1:2];//toShow.remove(p.getUniqueId());
			//toShow.putIfAbsent(p.getUniqueId(), new HashSet<UUID>());
			if(!show.isEmpty()) {
				for(UUID id : show) {
					Player o = Bukkit.getPlayer(id);
					sh++;
					if(o != null) {
						log.info(String.format("Showing %s to %s", o.getName(), p.getName()));
						p.showPlayer(o);
					}
				}
				show.clear(); // prepare buffer for next swap.
			}

			if (p.hasPermission("jammer.bypass")) continue;

			HashSet<UUID> hide = buffers[buff?3:4];//toHide.remove(p.getUniqueId());
			//toHide.putIfAbsent(p.getUniqueId(), new HashSet<UUID>());
			if(!hide.isEmpty()) {
				for(UUID id : hide) {
					Player o = Bukkit.getPlayer(id);
					hi++;
					if(o != null) {
						if(showCombatTagged && ctManager.isTagged(id)) continue;
						log.info(String.format("Hiding %s from %s", o.getName(), p.getName()));
						p.hidePlayer(o);
					}
				}
				hide.clear();
			}

			t = System.currentTimeMillis();
			aqp = aqp + ((double)(t-b) - aqp)/pl;
		}
		t = System.currentTimeMillis();
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
		HashSet<UUID>[] buffers = maps.get(player.getUniqueId());
		if (buffers == null) {
			maps.put(player.getUniqueId(), allocate());
		} else {
			for (HashSet<UUID> buffer : buffers){
				buffer.clear();
			}
		}
		/*hiddenMap.put(player.getUniqueId(), new HashSet<UUID>());
		toShow.put(player.getUniqueId(), new HashSet<UUID>());
		toHide.put(player.getUniqueId(), new HashSet<UUID>());*/
	}

	@SuppressWarnings("unchecked")
	private HashSet<UUID>[] allocate() {
		return (HashSet<UUID>[]) new HashSet[] { new HashSet<UUID>(), new HashSet<UUID>(), 
			new HashSet<UUID>(), new HashSet<UUID>(), new HashSet<UUID>() };
	}
	
	@EventHandler
	public void onPlayerLeave(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		lastLocations.remove(player);
		// allocation is expensive. In case they rejoin, leave the data where it was.
		/*hiddenMap.remove(player.getUniqueId());
		toShow.remove(player.getUniqueId());
		toHide.remove(player.getUniqueId());*/
	}
	
	@EventHandler
	public void onPlayerMove(PlayerMoveEvent event) {
		Player player = event.getPlayer();
		PlayerLocation location = new PlayerLocation(player.getEyeLocation(), player.getUniqueId());
		lastLocations.add(location);
		visThread.queueLocation(location);
	}
	
	class VisibilityThread extends Thread {
		
		private final ConcurrentLinkedQueue<PlayerLocation> playerQueue = new ConcurrentLinkedQueue<PlayerLocation>();
		
		public void run() {
			log.info("RadarJammer: Starting calculation thread!");
			while(true) {
				// TODO: always updating on every move sounds super expensive. Might be better
				// to build up a hashSet of players who have moved, then periodically update them 
				// all en mass.
				// Probably a good idea to bake in some throttling; LinkedQueues are totally unbounded
				// so if the rate at which players move >>> the rate of processing, we're screwed.
				PlayerLocation next = playerQueue.poll();
				if(next != null) {
					doCalculations(next);
				}
				if (calcRuns > 100) {
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
		
		private void queueLocation(PlayerLocation location) {
			if(location == null) return;
			playerQueue.offer(location);
		}
		
		private double calcSetupAverage = 0d;
		private double calcPerPlayerAverage = 0d;
		private double calcPerPlayerRuns = 0d;
		private double calcAverage = 0d;
		private double calcRuns = 0d;

		private void doCalculations(PlayerLocation location) {
			calcRuns ++;
			long s_ = System.currentTimeMillis();
			long b_ = 0l;
			long d_ = 0l;
			long e_ = 0l;
			// TODO: next target. This reduction is likely very expensive.
			HashSet<PlayerLocation> locations = new HashSet<PlayerLocation>();
			synchronized(lastLocations) {
				locations.addAll(lastLocations);
			}
			UUID id = location.getID();
			e_ = System.currentTimeMillis();
			calcSetupAverage += (double) (e_ - s_);
			for(PlayerLocation other : locations) {
				if(other.equals(location)) continue;
				calcPerPlayerRuns++;
				b_ = System.currentTimeMillis();
				UUID oid = other.getID();
				if (id.equals(oid)) continue;
				boolean hidePlayer, hideOther;
				double dist = location.getSquaredDistance(other);
				if(dist > minCheck) {
					if(dist < maxCheck) {
						hideOther = location.getAngle(other) > maxFov;
						hidePlayer = other.getAngle(location) > maxFov;
					} else {
						hidePlayer = hideOther = true;
					}
				} else {
					hidePlayer = hideOther = false;
				}
				HashSet<UUID>[] buffers = maps.get(id);
				if (buffers == null) return;

				boolean hidingOther = buffers[0].contains(oid); //hiddenMap.get(id).contains(oid);
				if(hidingOther != hideOther) {
					if(hideOther) {
						buffers[getBuffer(btype.HIDE)].add(oid);
						//toHide.get(id).add(oid);
						buffers[0].add(oid);
						//hiddenMap.get(id).add(oid);
					} else {
						buffers[getBuffer(btype.SHOW)].add(oid);
						//toShow.get(id).add(oid);
						buffers[0].remove(oid);
						//hiddenMap.get(id).remove(oid);
					}
				}

				buffers = maps.get(oid);

				boolean hidingPlayer = buffers[0].contains(id); //hiddenMap.get(oid).contains(id);
				if(hidingPlayer != hidePlayer) {
					if(hidePlayer) {
						buffers[getBuffer(btype.HIDE)].add(id);
						//toHide.get(oid).add(id);
						buffers[0].add(id);
						//hiddenMap.get(oid).add(id);
					} else {
						buffers[getBuffer(btype.SHOW)].add(id);
						//toShow.get(oid).add(id);
						buffers[0].remove(id);
						//hiddenMap.get(oid).remove(id);
					}
				}
				e_ = System.currentTimeMillis();
				calcPerPlayerAverage += (e_ - b_);
			}
			e_ = System.currentTimeMillis();
			calcAverage += (e_ - s_);
		}
	}
}
