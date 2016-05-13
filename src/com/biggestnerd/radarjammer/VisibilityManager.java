package com.biggestnerd.radarjammer;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map.Entry;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;

import net.md_5.bungee.api.ChatColor;

public class VisibilityManager extends BukkitRunnable implements Listener{
	
	private int minCheck;
	private int maxCheck;
	private double hFov;
	private double vFov;
	private boolean showCombatTagged;
	private boolean timing;
	private float maxSpin;
	private long flagTime;
	private int maxFlags;
	private int blindDuration;
	private long maxLogoutTime;
	
	private ConcurrentHashMap<UUID, HashSet<UUID>[]> maps;
	private ConcurrentHashMap<UUID, Long> blinded;
	private ConcurrentLinkedQueue<UUID> blindQueue;
	private ConcurrentHashMap<UUID, Integer> offlineTaskMap;
	private AtomicBoolean buffer;
	
	private CalculationThread calcThread;
	private AntiBypassThread antiBypassThread;
	private Logger log;
	private RadarJammer plugin;
	
	public VisibilityManager(RadarJammer plugin, int minCheck, int maxCheck, double hFov, double vFov, boolean showCombatTagged, boolean timing,
							float maxSpin, long flagTime, int maxFlags, int blindDuration, boolean loadtest, long maxLogoutTime) {
		this.plugin = plugin;
		log = plugin.getLogger();
		maps = new ConcurrentHashMap<UUID, HashSet<UUID>[]>();
		blinded = new ConcurrentHashMap<UUID, Long>();
		blindQueue = new ConcurrentLinkedQueue<UUID>();
		offlineTaskMap = new ConcurrentHashMap<UUID, Integer>();
		buffer = new AtomicBoolean();

		this.minCheck = minCheck*minCheck;
		this.maxCheck = maxCheck*maxCheck;
		this.hFov = hFov;
		this.vFov = vFov;
		boolean ctEnabled = plugin.getServer().getPluginManager().isPluginEnabled("CombatTagPlus");
		if(!ctEnabled || !showCombatTagged) {
			this.showCombatTagged = false;
		} else {
			log.info("RadarJammer will show combat tagged players.");
			this.showCombatTagged = true;
			CombatTagManager.initialize();
		}
		this.timing = timing;
		this.maxSpin = maxSpin;
		this.flagTime = flagTime;
		this.maxFlags = maxFlags;
		this.blindDuration = blindDuration;
		this.maxLogoutTime = maxLogoutTime;
		runTaskTimer(plugin, 1L, 1L);
		calcThread = new CalculationThread();
		calcThread.start();
		antiBypassThread = new AntiBypassThread();
		antiBypassThread.start();
		if(loadtest) new SyntheticLoadTest().runTaskTimerAsynchronously(plugin, 1L, 1L);
		log.info("VisibilityManager initialized!");
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
			
			if(blindQueue.remove(p.getUniqueId())) {
				int amplifyMin = blindDuration + (int) antiBypassThread.angleChange.get(p.getUniqueId())[3];
				int amplifyTicks = amplifyMin * 7200;
				p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, amplifyTicks, 1));
				p.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, amplifyTicks, 1));
				p.sendMessage(ChatColor.DARK_RED + "You're spinning too fast and dizziness sets in. You are temporarily blinded for " + amplifyMin + " minutes!");
			}
			
			// now get the set of arrays. Careful to only deal with the "locked" ones
			// based on the semaphore. In this case, "true" gives low-order buffers.
			UUID pu = p.getUniqueId();
			HashSet<UUID>[] buffers = maps.get(pu);
			if (buffers == null) {
				maps.put(pu, allocate());
				continue;
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
		PlayerLocation location = getLocation(player);
		calcThread.queueLocation(location);
		HashSet<UUID>[] buffers = maps.get(player.getUniqueId());
		if (buffers == null) {
			maps.put(player.getUniqueId(), allocate());
		} else {
			for (HashSet<UUID> buffer : buffers){
				buffer.clear();
			}
		}
		Integer task = offlineTaskMap.remove(player.getUniqueId());
		if(task != null) {
			plugin.getServer().getScheduler().cancelTask(task);
		}
	}
	
	@EventHandler
	public void onPlayerLeave(PlayerQuitEvent event) {
		UUID id = event.getPlayer().getUniqueId();
		OfflinePlayerCheck offlineTask = new OfflinePlayerCheck(id);
		offlineTaskMap.put(id, offlineTask.runTaskLater(plugin, maxLogoutTime).getTaskId());
	}

	@SuppressWarnings("unchecked")
	private HashSet<UUID>[] allocate() {
		return (HashSet<UUID>[]) new HashSet[] { new HashSet<UUID>(), new HashSet<UUID>(), 
			new HashSet<UUID>(), new HashSet<UUID>(), new HashSet<UUID>() };
	}
	
	@EventHandler
	public void onPlayerMove(PlayerMoveEvent event) {
		Player player = event.getPlayer();
		PlayerLocation location = getLocation(player);
		calcThread.queueLocation(location);
	}
	
	private PlayerLocation getLocation(Player player) {
		boolean invis = player.hasPotionEffect(PotionEffectType.INVISIBILITY);
		ItemStack inHand = player.getInventory().getItemInMainHand();
		ItemStack offHand = player.getInventory().getItemInOffHand();
		ItemStack[] armor = player.getInventory().getArmorContents();
		boolean hasArmor = false;
		for(ItemStack item : armor) if(item != null && item.getType() != Material.AIR) hasArmor = true;
		invis = invis && (inHand == null || inHand.getType() == Material.AIR) && (offHand == null || offHand.getType() == Material.AIR) && !hasArmor;
		return new PlayerLocation(player.getEyeLocation(), player.getUniqueId(), invis);
	}
	
	class CalculationThread extends Thread {
		private final ConcurrentHashMap<UUID, PlayerLocation> locationMap = new ConcurrentHashMap<UUID, PlayerLocation>();
		private final Set<UUID> movedPlayers = Collections.synchronizedSet(new LinkedHashSet<UUID>());
		private final Set<UUID> recalc = Collections.synchronizedSet(new HashSet<UUID>());
		public void run() {
			log.info("RadarJammer: Starting calculation thread!");
			long lastLoopStart = 0l;
			while(true) {
				lastLoopStart = System.currentTimeMillis();
				while (System.currentTimeMillis() - lastLoopStart < 50l) {
					if(movedPlayers.iterator().hasNext()) {
						UUID id = movedPlayers.iterator().next();
						PlayerLocation next = locationMap.get(id);
						if (next != null) {
							recalc.add(id);
							movedPlayers.remove(id);
						}
						try {
							sleep(1l);
						}catch(InterruptedException ie) {}
					}
				}
				if(!recalc.isEmpty()) {
					doCalculations();
				}
				if (calcRuns > 20) {
					showAvg();
				}
				try {
					sleep(1l);
				}catch(InterruptedException ie) {}
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
			locationMap.put(location.getID(), location);
			movedPlayers.add(location.getID());
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
			for (UUID player : recalc) {
				doCalculations(locationMap.get(player));
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
			Enumeration<PlayerLocation> players = locationMap.elements();
			while(players.hasMoreElements()) {
				PlayerLocation other = players.nextElement();
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
			if(showCombatTagged && CombatTagManager.isTagged(loc.getID())) return false;
			boolean blind = blinded.containsKey(loc.getID());
			if(blind || other.isInvis()) return true;
			double dist = loc.getSquaredDistance(other);
			if(dist > minCheck) {
				if(dist < maxCheck) {
					double vAngle = loc.getVerticalAngle(other);
					double hAngle = loc.getHorizontalAngle(other);
					return !(vAngle < vFov && hAngle < hFov);
				} else {
					return true;
				}
			} else {
				return false;
			}
		}
	}
	
	class AntiBypassThread extends Thread {
		
		private ConcurrentHashMap<UUID, float[]> angleChange = new ConcurrentHashMap<UUID, float[]>();
		
		public AntiBypassThread() {
			ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Client.LOOK, PacketType.Play.Client.POSITION_LOOK) {
				@Override
				public void onPacketReceiving(PacketEvent event) {
					PacketContainer packet = event.getPacket();
					float yaw = packet.getFloat().read(0);
					updatePlayer(event.getPlayer().getUniqueId(), yaw);
				}
			});
		}
		
		@Override
		public void run() {
			long lastLoopStart = 0l;
			long lastFlagCheck = 0l;
			while(true) {
				if(System.currentTimeMillis() - lastFlagCheck > flagTime) {
					lastFlagCheck = System.currentTimeMillis();
					for(Entry<UUID, float[]> entry : angleChange.entrySet()) {
						if(entry.getValue()[2] >= maxFlags) {
							angleChange.get(entry.getKey())[3] += 1;
							UUID id = entry.getKey();
							log.info(id + " has reached the threshold for bypass flags, blinding them for a few minutes.");
							blinded.put(id, System.currentTimeMillis());
							entry.getValue()[2] = 0;
						}
					}
				}
				//Every second check angle change
				if(System.currentTimeMillis() - lastLoopStart > 1000l) {
					lastLoopStart = System.currentTimeMillis();
					for(Entry<UUID, Long> entry : blinded.entrySet()) {
						long blindLengthMillis = blindDuration * 60000;
						if(System.currentTimeMillis() - entry.getValue() > blindLengthMillis) {
							blinded.remove(entry.getKey());
						}
					}
					for(Entry<UUID, float[]> entry : angleChange.entrySet()) {
						boolean blind = entry.getValue()[0] > maxSpin;
						entry.getValue()[0] = 0;
						if(blind) {
							entry.getValue()[2] += 1;
						}
					}
				}
				try {
					sleep(1l);
				}catch(InterruptedException ie) {}
			}
		}
		
		private void updatePlayer(UUID player, float newAngle) {
			if(!angleChange.containsKey(player)) {
				angleChange.put(player, new float[]{0,newAngle,0,0});
			} else {
				float last = angleChange.get(player)[1];
				float change = Math.abs(last - newAngle);
				angleChange.get(player)[1] = newAngle;
				angleChange.get(player)[0] += change;
			}
		}
	}
	
	class SyntheticLoadTest extends BukkitRunnable {
		
		private PlayerLocation[] locations;
		
		public SyntheticLoadTest() {
			locations = new PlayerLocation[100];
			int i = 0;
			for(int x = 0; x < 10; x++) {
				for(int z = 0; z < 10; z++) {
					UUID id = UUID.randomUUID();

					PlayerLocation location = new PlayerLocation(x * 10, 60, z * 10, 0, 0, id, false);
					locations[i++] = location;
					calcThread.queueLocation(location);
					HashSet<UUID>[] buffers = maps.get(id);
					if (buffers == null) {
						maps.put(id, allocate());
					} else {
						for (HashSet<UUID> buffer : buffers){
							buffer.clear();
						}
					}
				}
			}
		}
		
		@Override
		public void run() {
			for(int i = 0; i < locations.length; i++) {
				if(locations[i] == null) break;
				locations[i].addYaw(.5F);
				calcThread.queueLocation(locations[i]);
			}
		}
	}
	
	class OfflinePlayerCheck extends BukkitRunnable {
		private UUID id;
		
		public OfflinePlayerCheck(UUID player) {
			this.id = player;
		}
		
		@Override
		public void run () {
			Player player = Bukkit.getPlayer(id);
			if(player == null || !player.isOnline()) {
				maps.remove(id);
				blinded.remove(id);
				blindQueue.remove(id);
				offlineTaskMap.remove(player);
			}
		}
	}
}
