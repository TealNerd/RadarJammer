package com.biggestnerd.radarjammer;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.util.Vector;

public class PlayerLocation {
	
	private static final double degrees = 180 / Math.PI;
	private double x;
	private double y;
	private double z;
	private float yaw;
	private float pitch;
	private UUID id;
	private boolean invis;
	private Vector direction;
	
	public PlayerLocation(double x, double y, double z, Vector direction, UUID id, boolean invis) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.direction = direction;
		this.id = id;
		this.invis = invis;
	}
	
	public PlayerLocation(Location loc, UUID id, boolean invis) {
		this(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), loc.getDirection(), id, invis);
	}
	
	public double getAngle(PlayerLocation other) {
		Vector toPlayer = new Vector(other.x - x, other.z - z, other.y - y);
		return direction.angle(toPlayer) * degrees;
	}

	public double getSquaredDistance(PlayerLocation loc){
		double dx = x - loc.x;
		double dy = y - loc.y;
		double dz = z - loc.z;
		return dx * dx + dy * dy + dz * dz;
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
