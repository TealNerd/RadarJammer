package com.biggestnerd.radarjammer;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.util.Vector;
public class PlayerLocation {
	
	private static final double degrees = 180 / Math.PI;
	private static final double radians = Math.PI / 180;
	private double x;
	private double y;
	private double z;
	private float yaw;
	private float pitch;
	private UUID id;
	private boolean invis;
	
	public PlayerLocation(double x, double y, double z, float yaw, float pitch, UUID id, boolean invis, 
			boolean selfie) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.yaw = yaw;
		this.pitch = pitch;
		if(selfie) {
			yaw -= 180;
			pitch = -pitch;
		}
		this.id = id;
		this.invis = invis;
	}
	
	public PlayerLocation(Location loc, UUID id, boolean invis, boolean selfie) {
		this(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), loc.getYaw(), loc.getPitch(), id, invis, selfie);
	}

	public double getVerticalAngle(PlayerLocation other) {
		PlayerLocation corner = new PlayerLocation(other.x, y, other.z, 0, 0, null, false, false);
		double vRads = Math.atan(corner.getDistance(other) / corner.getDistance(this));
		double vAngle = vRads * degrees;
		return Math.abs(vAngle - pitch);
	}

	public double getHorizontalAngle(PlayerLocation other) {
		Vector lookDirection = getLookVector();
		Vector to = new Vector(other.x, 0, other.z);
		Vector from = new Vector(x, 0, z);
		Vector vec = to.subtract(from);
		return lookDirection.angle(vec) * degrees;
	}
	
	private Vector getLookVector() {
		Vector vec = new Vector();
		vec.setY(0);
		double yawRads = yaw * radians;
		vec.setX(-1 * Math.sin(yawRads));
		vec.setZ(Math.cos(yawRads));
		return vec;
	}
	
	public double getSquaredDistance(PlayerLocation loc){
		double dx = x - loc.x;
		double dy = y - loc.y;
		double dz = z - loc.z;
		return dx * dx + dy * dy + dz * dz;
	}
	
	public double getDistance(PlayerLocation loc) {
		double dx = x - loc.x;
		double dy = y - loc.y;
		double dz = z - loc.z;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
	
	@Override
	public boolean equals(Object other) {
		if(!(other instanceof PlayerLocation))
			return false;
		PlayerLocation loc = (PlayerLocation) other;
		if(id.equals(loc.id)) return true;
		return x == loc.x && y == loc.y && z == loc.z && pitch == loc.pitch && yaw == loc.yaw && id.equals(loc.id);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	public boolean hasMoved(PlayerLocation other) {
		return other.x == x && other.y == y && other.z == z;
	}
	
	public UUID getID() {
		return id;
	}
	
	public boolean isInvis() {
		return invis;
	}
	
	public void addYaw(float toAdd) {
		yaw += toAdd;
	}
}
