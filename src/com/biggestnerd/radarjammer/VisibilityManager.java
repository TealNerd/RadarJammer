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
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import net.minelink.ctplus.CombatTagPlus;
import net.minelink.ctplus.TagManager;

public class VisibilityManager extends BukkitRunnable implements Listener{
	
	private final int minCheck;
	private final int maxCheck;
	private final double maxFov;
	private final boolean showCombatTagged;
	private boolean trueInvis;
	private boolean timing;
	
	private ConcurrentHashMap<UUID, HashSet<UUID>[]> maps;
	private AtomicBoolean buffer;
	
	private CalculationThread calcThread;
	private TagManager ctManager;
	private Logger log;
	
	public VisibilityManager(RadarJammer plugin, int minCheck, int maxCheck, double maxFov, boolean showCombatTagged, boolean trueInvis, boolean timing) {
		log = plugin.getLogger();
		maps = new ConcurrentHashMap<UUID, HashSet<UUID>[]>();
		buffer = new AtomicBoolean();

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
		this.trueInvis = trueInvis;
		this.timing = timing;
		runTaskTimer(plugin, 1L, 1L);
		calcThread = new CalculationThread();
		calcThread.start();
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
			if(timing) {
				b = System.currentTimeMillis();
				pl++;
			}
			// now get the set of arrays. Careful to only deal with the "locked" ones
			// based on the semaphore. In this case, "true" gives low-order buffers.
			UUID pu = p.getUniqueId();
			HashSet<UUID>[] buffers = maps.get(pu);
			if (buffers == null) {
				maps.put(pu, allocate());
			}

			HashSet<UUID> show = buffers[buff?1:2];
			HashSet<UUID> hide = buffers[buff?3:4];
			if(!show.isEmpty()) {
				for(UUID id : show) {
					Player o = Bukkit.getPlayer(id);
					if(timing) sh++;
					if(o != null) {
						log.info(String.format("Showing %s to %s", o.getName(), p.getName()));
						p.showPlayer(o);
						if (hide.remove(id)) { // prefer to show rather then hide. In case of conflict, show wins.
							log.info(String.format("Suppressed hide of %s from %s", o.getName(), p.getName()));
						}
					}
				}
				show.clear(); // prepare buffer for next swap.
			}

			if (p.hasPermission("jammer.bypass")) continue;

			if(!hide.isEmpty()) {
				for(UUID id : hide) {
					Player o = Bukkit.getPlayer(id);
					if(timing) hi++;
					if(o != null) {
						log.info(String.format("Hiding %s from %s", o.getName(), p.getName()));
						p.hidePlayer(o);
					}
				}
				hide.clear();
			}

			if(timing) {
				t = System.currentTimeMillis();
				aqp = aqp + ((double)(t-b) - aqp)/pl;
			}
		}
		if ((s - lastCheckRun) > 1000l && timing) {
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
		boolean invis = player.hasPotionEffect(PotionEffectType.INVISIBILITY);
		if(trueInvis) {
			ItemStack inHand = player.getItemInHand();
			ItemStack[] armor = player.getInventory().getArmorContents();
			boolean hasArmor = false;
			for(ItemStack item : armor) if(item != null && item.getType() != Material.AIR) hasArmor = true;
			invis = invis && (inHand == null || inHand.getType() == Material.AIR) && hasArmor;
		}
		PlayerLocation location = new PlayerLocation(player.getEyeLocation(), player.getUniqueId(), invis);
		calcThread.queueLocation(location);
		HashSet<UUID>[] buffers = maps.get(player.getUniqueId());
		if (buffers == null) {
			maps.put(player.getUniqueId(), allocate());
		} else {
			for (HashSet<UUID> buffer : buffers){
				buffer.clear();
			}
		}
	}

	@SuppressWarnings("unchecked")
	private HashSet<UUID>[] allocate() {
		return (HashSet<UUID>[]) new HashSet[] { new HashSet<UUID>(), new HashSet<UUID>(), 
			new HashSet<UUID>(), new HashSet<UUID>(), new HashSet<UUID>() };
	}
	
	@EventHandler
	public void onPlayerLeave(PlayerQuitEvent event) {
		//Player player = event.getPlayer();
		//lastLocations.remove(player);
		// TODO: While allocation is expensive, keeping allocations forever is bad for long
		// running servers w/ constrained memory.
		// To strike a balance, add a LIFO queue here and schedule a task to periodically release
		// data from old players who haven't returned.
	}
	
	@EventHandler
	public void onPlayerMove(PlayerMoveEvent event) {
		Player player = event.getPlayer();
		boolean invis = player.hasPotionEffect(PotionEffectType.INVISIBILITY);
		if(trueInvis) {
			ItemStack inHand = player.getItemInHand();
			ItemStack[] armor = player.getInventory().getArmorContents();
			boolean hasArmor = false;
			for(ItemStack item : armor) if(item != null && item.getType() != Material.AIR) hasArmor = true;
			invis = invis && (inHand == null || inHand.getType() == Material.AIR) && hasArmor;
		}
		PlayerLocation location = new PlayerLocation(player.getEyeLocation(), player.getUniqueId(), invis);
		calcThread.queueLocation(location);
	}
	
	class CalculationThread extends Thread {
		
		private final ConcurrentLinkedQueue<PlayerLocation> playerQueue = new ConcurrentLinkedQueue<PlayerLocation>();
		private final Set<PlayerLocation> lastLocations = Collections.synchronizedSet(new HashSet<PlayerLocation>());
		private final Set<PlayerLocation> recalc = Collections.synchronizedSet(new HashSet<PlayerLocation>());
		
		public void run() {
			log.info("RadarJammer: Starting calculation thread!");
			long lastLoopStart = 0l;
			while(true) {
				// TODO:
				// Probably a good idea to bake in some throttling; LinkedQueues are totally unbounded
				// so if the rate at which players move >>> the rate of processing, we're screwed.
				lastLoopStart = System.currentTimeMillis();
				while (System.currentTimeMillis() - lastLoopStart < 50l) {
					PlayerLocation next = playerQueue.poll();
					if (next != null) {
						lastLocations.add(next);
						recalc.add(next);
					}
					try {
						sleep(0l);
					}catch(InterruptedException ie) {}
				}
				if(!recalc.isEmpty()) {
					doCalculations();
				}
				if (calcRuns > 20) {
					showAvg();
				}
			}
		}

		private void showAvg() {
			if(!timing) return;
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
		long s_ = 0L;
		long b_ = 0l;
		long e_ = 0l;

		private void doCalculations() {
			for (PlayerLocation pl : recalc) {
				doCalculations(pl);
			}
			recalc.clear();
		}

		private void doCalculations(PlayerLocation location) {
			if (location == null) return;
			if(timing) {
				calcRuns ++;
				s_ = System.currentTimeMillis();
				e_ = System.currentTimeMillis();
				calcSetupAverage += (double) (e_ - s_);
			}
			UUID id = location.getID();
			for(PlayerLocation other : lastLocations) {
				UUID oid = other.getID();
				if (id.equals(oid)) continue;
				if(timing) {
					calcPerPlayerRuns++;
					b_ = System.currentTimeMillis();
				}
				boolean hidePlayer = shouldHide(other, location); 
				boolean hideOther = shouldHide(location, other);
				/*
				double dist = location.getSquaredDistance(other);
				if(dist > minCheck) {
					if(dist < maxCheck) {
						hideOther = location.getAngle(other) > maxFov || other.isInvis();
						hidePlayer = other.getAngle(location) > maxFov || location.isInvis();
					} else {
						hidePlayer = hideOther = true;
					}
				} else {
					hidePlayer = hideOther = false;
				}
				*/
				HashSet<UUID>[] buffers = maps.get(id);
				if (buffers == null) return;

				boolean hidingOther = buffers[0].contains(oid);
				if(hidingOther != hideOther) {
					if(hideOther) {
						buffers[getBuffer(btype.HIDE)].add(oid);
						buffers[0].add(oid);
					} else {
						buffers[getBuffer(btype.SHOW)].add(oid);
						buffers[0].remove(oid);
					}
				}

				buffers = maps.get(oid);
				if (buffers == null) return;

				boolean hidingPlayer = buffers[0].contains(id);
				if(hidingPlayer != hidePlayer) {
					if(hidePlayer) {
						buffers[getBuffer(btype.HIDE)].add(id);
						buffers[0].add(id);
					} else {
						buffers[getBuffer(btype.SHOW)].add(id);
						buffers[0].remove(id);
					}
				}
				if(timing) {
					e_ = System.currentTimeMillis();
					calcPerPlayerAverage += (e_ - b_);
				}
			}
			if(timing) {
				e_ = System.currentTimeMillis();
				calcAverage += (e_ - s_);
			}
		}
		
		private boolean shouldHide(PlayerLocation loc, PlayerLocation other) {
			double dist = loc.getDistance(other);
			if(ctManager.isTagged(loc.getID())) return false;
			if(loc.isBlind() || other.isInvis()) return true;
			if(dist > minCheck) {
				if(dist < maxCheck) {
					return loc.getAngle(other) > maxFov;
				} else {
					return true;
				}
			} else {
				return false;
			}
		}
	}
}
