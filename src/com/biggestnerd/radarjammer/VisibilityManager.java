package com.biggestnerd.radarjammer;

import static com.comphenix.protocol.PacketType.Play.Server.*;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_10_R1.entity.CraftPlayer;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.SplashPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;

import com.biggestnerd.radarjammer.packets.WrapperPlayServerEntityDestroy;
import com.biggestnerd.radarjammer.packets.WrapperPlayServerMount;
import com.biggestnerd.radarjammer.packets.WrapperPlayServerSpawnEntity;
import com.biggestnerd.radarjammer.packets.WrapperPlayServerSpawnEntityLiving;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;

import net.minecraft.server.v1_10_R1.SoundCategory;

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
	private boolean entities;
	private boolean suppress;
	private boolean vanish;
	
	private ConcurrentHashMap<UUID, HashSet<UUID>[]> maps;
	private ConcurrentHashMap<UUID, HashSet<Integer>> entityMaps;
	private ConcurrentHashMap<UUID, Integer> pingMap;
	private ConcurrentHashMap<UUID, Long> blinded;
	private ConcurrentLinkedQueue<UUID> blindQueue;
	private ConcurrentHashMap<UUID, Integer> offlineTaskMap;
	private Set<UUID> selfieMode;
	private AtomicBoolean buffer;
	private final Set<UUID> inQueue = Collections.synchronizedSet(new HashSet<UUID>());
	private final ConcurrentHashMap<UUID, PlayerLocation> locationMap = new ConcurrentHashMap<UUID, PlayerLocation>();
	private final ConcurrentHashMap<UUID, PlayerLocation> newLocations = new ConcurrentHashMap<UUID, PlayerLocation>();
	private final ExecutorService executor = Executors.newFixedThreadPool(5);

	private AntiBypassThread antiBypassThread;
	private Logger log;
	private RadarJammer plugin;
	
	public VisibilityManager(RadarJammer plugin, int minCheck, int maxCheck, double hFov, double vFov,
			boolean showCombatTagged, boolean timing, float maxSpin, long flagTime, int maxFlags, int blindDuration,
			boolean loadtest, long maxLogoutTime, boolean entities, boolean suppress) {
		this.plugin = plugin;
		log = plugin.getLogger();
		maps = new ConcurrentHashMap<UUID, HashSet<UUID>[]>();
		pingMap = new ConcurrentHashMap<UUID, Integer>();
		if(entities)entityMaps = new ConcurrentHashMap<UUID, HashSet<Integer>>();
		blinded = new ConcurrentHashMap<UUID, Long>();
		blindQueue = new ConcurrentLinkedQueue<UUID>();
		offlineTaskMap = new ConcurrentHashMap<UUID, Integer>();
		selfieMode = Collections.synchronizedSet(new HashSet<UUID>());
		buffer = new AtomicBoolean();
		this.suppress = suppress;

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
		}
		vanish = plugin.getServer().getPluginManager().isPluginEnabled("VanishNoPacket");
		this.timing = timing;
		this.maxSpin = maxSpin;
		this.flagTime = flagTime;
		this.maxFlags = maxFlags;
		this.blindDuration = blindDuration;
		this.maxLogoutTime = maxLogoutTime;
		this.entities = entities;
		runTaskTimer(plugin, 1L, 1L);
		if(entities) {
			registerEntityPacketListeners();
		}
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
			pingMap.put(p.getUniqueId(), getPing(p));
			if(timing) {
				b = System.currentTimeMillis();
				pl++;
			}
			
			if(blindQueue.remove(p.getUniqueId())) {
				int amplifyMin = blindDuration + (int) antiBypassThread.angleChange.get(p.getUniqueId())[3];
				int amplifyTicks = amplifyMin * 7200;
				p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, amplifyTicks, 1));
				p.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, amplifyTicks, 1));
				p.sendMessage(ChatColor.DARK_RED + "You're spinning too fast and dizziness sets in. "
						+ "You are temporarily blinded for " + amplifyMin + " minutes!");
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
						if(vanish && CompatManager.isVanished(o)) continue;
						if(!suppress) log.info(String.format("Showing %s to %s", o.getName(), p.getName()));
						p.showPlayer(o);
						if(o.getVehicle() != null) {
							sendMountPacket(o, p);
						}
						if (hide.remove(id)) { // prefer to show rather then hide. In case of conflict, show wins.
							if(!suppress) log.info(String.format("Suppressed hide of %s from %s", 
									o.getName(), p.getName()));
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
						if(!suppress) log.info(String.format("Hiding %s from %s", o.getName(), p.getName()));
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
				log.info(String.format("Updated %d players in %d milliseconds, spending %.2f per player."
						+ " Total %d seen updates and %d hide updates", pl, (t-s), aqp, sh, hi));
			else
				log.info("No players currently tracked.");
			lastCheckRun = s;
		}
	}

	private void sendMountPacket(Player mounted, Player player){
		Entity vehicle = mounted.getVehicle();
		WrapperPlayServerMount packet = new WrapperPlayServerMount();
		packet.setVehicleEID(vehicle.getEntityId());
		packet.setPassengerEIDs(mounted.getEntityId());
		packet.sendPacket(player);
	}
	
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		PlayerLocation location = getLocation(player);
		recalcPlayer(location);
		pingMap.put(player.getUniqueId(), getPing(player));
		HashSet<UUID>[] buffers = maps.get(player.getUniqueId());
		if (buffers == null) {
			maps.put(player.getUniqueId(), allocate());
		} else {
			for (HashSet<UUID> buffer : buffers){
				buffer.clear();
			}
		}
		
		if(entities) {
			HashSet<Integer> entityBuffer = entityMaps.get(player.getUniqueId());
			if(entityBuffer == null) {
				entityMaps.put(player.getUniqueId(), new HashSet<Integer>());
			} else {
				entityBuffer.clear();
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
		recalcPlayer(location);
	}
	
	private PlayerLocation getLocation(Player player) {
		boolean invis = player.hasPotionEffect(PotionEffectType.INVISIBILITY);
		if(vanish) invis = invis || CompatManager.isVanished(player);
		ItemStack inHand = player.getInventory().getItemInMainHand();
		ItemStack offHand = player.getInventory().getItemInOffHand();
		ItemStack[] armor = player.getInventory().getArmorContents();
		boolean hasArmor = false;
		for(ItemStack item : armor) if(item != null && item.getType() != Material.AIR) hasArmor = true;
		invis = invis && (inHand == null || inHand.getType() == Material.AIR) && 
				(offHand == null || offHand.getType() == Material.AIR) && !hasArmor;
		boolean selfie = selfieMode.contains(player.getUniqueId());
		return new PlayerLocation(player.getEyeLocation(), player.getUniqueId(), invis, selfie);
	}
	
	@SuppressWarnings("deprecation")
	private void showEntity(Player player, Entity e) {
		if(e instanceof LivingEntity) {
			WrapperPlayServerSpawnEntityLiving packet = new WrapperPlayServerSpawnEntityLiving(e);
			packet.sendPacket(player);
		} else {
			int data = 0;
			if(e instanceof ItemFrame) {
				ItemFrame frame = (ItemFrame) e;
				switch(frame.getFacing()) {
				case EAST: data = 3;
					break;
				case NORTH: data = 2;
					break;
				case WEST: data = 1;
					break;
				default:
					break;
				
				}
			} else if(e instanceof FallingBlock) {
				FallingBlock block = (FallingBlock) e;
				data = block.getBlockId() | block.getBlockData() << 0xC;	
			} else if(e instanceof SplashPotion) {
				SplashPotion potion = (SplashPotion) e;
				data = potion.getItem().getDurability();
			} else if(e instanceof Projectile) {
				Projectile p = (Projectile) e;
				ProjectileSource shooter = p.getShooter();
				if(shooter instanceof Entity) {
					data = ((Entity) shooter).getEntityId();
					if((e instanceof Arrow) || (e instanceof SpectralArrow)) {
						data++;
					}
				}
			} else if(e instanceof Minecart) {
				switch (e.getType()) {
				case MINECART_CHEST: data = 1;
					break;
				case MINECART_FURNACE: data = 2;
					break;
				case MINECART_TNT: data = 3;
					break;
				case MINECART_MOB_SPAWNER: data = 4;
					break;
				case MINECART_HOPPER: data = 5;
					break;
				case MINECART_COMMAND: data = 6;
					break;
				default: break;
				}
			}
			int type = 0;
			switch (e.getType()) {
			case AREA_EFFECT_CLOUD: type = 3;
				break;
			case ARMOR_STAND: type = 78;
				break;
			case ARROW: type = 60;
				break;
			case BOAT: type = 1;
				break;
			case DROPPED_ITEM: type = 2;
				break;
			case EGG: type = 62;
				break;
			case ENDER_CRYSTAL: type = 51;
				break;
			case ENDER_PEARL: type = 65;
				break;
			case ENDER_SIGNAL:
				break;
			case FALLING_BLOCK: type = 70;
				break;
			case FIREBALL: type = 63;
				break;
			case FIREWORK: type = 76;
				break;
			case ITEM_FRAME: type = 71;
				break;
			case LEASH_HITCH: type = 77;
				break;
			case MINECART:
			case MINECART_CHEST:
			case MINECART_COMMAND:
			case MINECART_FURNACE:
			case MINECART_HOPPER:
			case MINECART_MOB_SPAWNER:
			case MINECART_TNT: type = 10;
				break;
			case PRIMED_TNT: type = 50;
				break;
			case SHULKER_BULLET: type = 67;
				break;
			case SMALL_FIREBALL: type = 64;
				break;
			case SNOWBALL: type = 61;
				break;
			case SPECTRAL_ARROW: type = 91;
				break;
			case SPLASH_POTION: type = 73;
				break;
			case THROWN_EXP_BOTTLE: type = 75;
				break;
			case TIPPED_ARROW: type = 92;
				break;
			case WITHER_SKULL: type = 66;
				break;
			default:
				break;
			
			}
			WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(e, type, data);
			packet.sendPacket(player);
		}
	}
	
	private void hideEntities(Player player, Integer... ids) {
		WrapperPlayServerEntityDestroy packet = new WrapperPlayServerEntityDestroy();
		int[] arr = new int[ids.length];
		for(int i = 0; i < arr.length; i++) {
			arr[i] = ids[i];
		}
		packet.setEntityIds(arr);
		packet.sendPacket(player);
	}
	
	private Entity getEntityById(World world, int i) {
		for(Entity e : world.getEntities()) {
			if(e.getEntityId() == i) return e;
		}
		return null;
	}
	
	private int getPing(Player player) {
		return ((CraftPlayer)player).getHandle().ping;
	}
	
	private void registerEntityPacketListeners() {
		ProtocolLibrary.getProtocolManager().addPacketListener(
			new PacketAdapter(plugin, ListenerPriority.NORMAL, ENTITY_STATUS, REL_ENTITY_MOVE, ENTITY_LOOK, ENTITY, 
					ENTITY_HEAD_ROTATION, ENTITY_METADATA, ENTITY_VELOCITY, ENTITY_EQUIPMENT, ENTITY_TELEPORT, 
					ENTITY_EFFECT, REMOVE_ENTITY_EFFECT, SPAWN_ENTITY, SPAWN_ENTITY_LIVING) {
				@Override
				public void onPacketSending(PacketEvent event) {
					UUID player = event.getPlayer().getUniqueId();
					PlayerLocation loc = locationMap.get(player);
					if(loc == null || !entityMaps.containsKey(player)) return;
					PacketContainer packet = event.getPacket();
					int id = packet.getIntegers().read(0);
					Entity e = getEntityById(event.getPlayer().getWorld(), id);
					if((e instanceof Player)) return; //we handle players elsewhere
					if(e != null) { //just in case
						HashSet<Integer> hidden = entityMaps.get(player);
						Location location = e.getLocation();
						PlayerLocation eLoc = new PlayerLocation(location.getX(), location.getY(), location.getZ(),
								0, 0, null, false, false);
						double dist = loc.getSquaredDistance(eLoc);
						boolean hide = false;
						if(dist > minCheck) {
							if(dist < maxCheck) {
								double hAngle = loc.getHorizontalAngle(eLoc);
								double vAngle = loc.getVerticalAngle(eLoc);
								hide = hAngle > hFov || vAngle > vFov;
							} else {
								hide = true;
							}
						}
						if(hide) event.setCancelled(true);
						boolean hiding = hidden.contains(id);
						if(hide != hiding) {
							if(hide) {
								hidden.add(id);
								hideEntities(event.getPlayer(), id);
							} else {
								hidden.remove(id);
								showEntity(event.getPlayer(), e);
							}
						}
					}
				}
			});
		ProtocolLibrary.getProtocolManager().addPacketListener(
			new PacketAdapter(plugin, ListenerPriority.NORMAL, NAMED_SOUND_EFFECT) {
				@Override
				public void onPacketSending(PacketEvent event) {
					PacketContainer packet = event.getPacket();
					Location loc = event.getPlayer().getLocation();
					SoundCategory cat = packet.getSpecificModifier(SoundCategory.class).read(0);
					if(cat == SoundCategory.NEUTRAL || cat == SoundCategory.HOSTILE) {
						packet.getIntegers().write(0, loc.getBlockX() * 8);
						packet.getIntegers().write(1, loc.getBlockY() * 8);
						packet.getIntegers().write(2, loc.getBlockZ() * 8);
						packet.getFloat().write(0, (float)Math.min(packet.getFloat().read(0) / 5, 0.2));
					}
				}
			});
	}
	
	public void toggleSelfieMode(Player player) {
		UUID id = player.getUniqueId();
		if(selfieMode.add(id)) {
			player.sendMessage(ChatColor.DARK_AQUA + "You are now in selfie mode.");
		} else {
			selfieMode.remove(id);
			player.sendMessage(ChatColor.DARK_AQUA + "You have left selfie mode.");
		}
	}
	
	public boolean isInSelfieMode(Player player) {
		return selfieMode.contains(player.getUniqueId());
	}
	
	class CalculationTask implements Runnable {
		private final UUID player;
		
		public CalculationTask(UUID player) {
			this.player = player;
		}
		
		@Override
		public void run() {
			PlayerLocation loc = newLocations.remove(player);
			boolean coordMove = true;
			if(locationMap.containsKey(player)) {
				coordMove = locationMap.get(player).hasMoved(loc);
			}
			locationMap.put(player, loc);
			Enumeration<PlayerLocation> players = locationMap.elements();
			while(players.hasMoreElements()) {
				PlayerLocation other = players.nextElement();
				UUID oid = other.getID();
				if (player.equals(oid)) continue;
				HashSet<UUID>[] buffers = maps.get(player);
				if (buffers == null) return;

				boolean hideOther = shouldHide(loc, other);
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

				//Now only recalculate everyone else's view if the player physically moved
				//If they only turned their head (!coordMove) then everyone else's view wont have changed
				if(coordMove) {
					buffers = maps.get(oid);
					if (buffers == null) return;

					boolean hidePlayer = shouldHide(other, loc); 
					boolean hidingPlayer = buffers[0].contains(player);
					if(hidingPlayer != hidePlayer) {
						if(hidePlayer) {
							buffers[getBuffer(btype.HIDE)].add(player);
							buffers[0].add(player);
						} else {
							buffers[getBuffer(btype.SHOW)].add(player);
							buffers[0].remove(player);
						}
					}
				}
			}
		}
	}
	
	private boolean shouldHide(PlayerLocation loc, PlayerLocation other) {
		if(showCombatTagged && CompatManager.isTagged(other.getID())) return false;
		boolean blind = blinded.containsKey(loc.getID());
		if(blind || other.isInvis()) return true;
		double dist = loc.getSquaredDistance(other);
		if(dist > minCheck) {
			if(dist < maxCheck) {
				Integer ping = pingMap.get(loc.getID());
				if(ping == null) ping = 0;
				double vAngle = loc.getVerticalAngle(other);
				double hAngle = loc.getHorizontalAngle(other);
				return !(vAngle < adjustVfov(ping) && hAngle < adjustHfov(ping));
			} else {
				return true;
			}
		} else {
			return false;
		}
	}
	
	private static final double ratio = 15/500;
	private double adjustHfov(int ping) {
		double adjust = ratio * ping;
		adjust = Math.min(Math.max(0, adjust), 15);
		return hFov * (1 + (adjust / 100));
	}
	
	private double adjustVfov(int ping) {
		double adjust = ratio * ping;
		adjust = Math.min(Math.max(0, adjust), 15);
		return vFov * (1 + (adjust / 100));
	}
	
	private void recalcPlayer(PlayerLocation loc) {
		if(!inQueue.contains(loc.getID())) {
			executor.submit(new CalculationTask(loc.getID()));
			inQueue.add(loc.getID());
		}
		newLocations.put(loc.getID(), loc);
	}
	
	class AntiBypassThread extends Thread {
		
		private ConcurrentHashMap<UUID, float[]> angleChange = new ConcurrentHashMap<UUID, float[]>();
		
		public AntiBypassThread() {
			ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
					PacketType.Play.Client.LOOK, PacketType.Play.Client.POSITION_LOOK) {
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
							log.info(id + " has reached the threshold for bypass flags, "
									+ "blinding them for a few minutes.");
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

					PlayerLocation location = new PlayerLocation(x * 10, 60, z * 10, 0, 0, id, false, false);
					locations[i++] = location;
					recalcPlayer(location);
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
				recalcPlayer(locations[i]);
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
			OfflinePlayer player = Bukkit.getOfflinePlayer(id);
			if(!player.isOnline()) {
				maps.remove(id);
				blinded.remove(id);
				blindQueue.remove(id);
				offlineTaskMap.remove(player);
				if(entities) {
					entityMaps.remove(id);
				}
			}
		}
	}
}
