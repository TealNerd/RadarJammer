package com.biggestnerd.radarjammer;

import java.util.UUID;

import org.bukkit.Location;

public class PlayerLocation {
	
	private static final double degrees = 180 / Math.PI;
	private double x;
	private double y;
	private double z;
	private float yaw;
	private float pitch;
	private UUID id;
	private boolean invis;
	
	public PlayerLocation(double x, double y, double z, float yaw, float pitch, UUID id, boolean invis) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.yaw = yaw;
		this.pitch = pitch;
		this.id = id;
		this.invis = invis;
	}
	
	public PlayerLocation(Location loc, UUID id, boolean invis) {
		this(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), loc.getYaw(), loc.getPitch(), id, invis);
	}
	
	public double getVerticalAngle(PlayerLocation other) {
		PlayerLocation corner = new PlayerLocation(other.x, y, other.z, 0, 0, null, false);
		double vRads = Math.atan(corner.getDistance(other) / corner.getDistance(this));
		double vAngle = vRads * degrees;
		return Math.abs(vAngle - pitch);
	}
	
	public double getHorizontalAngle(PlayerLocation other) {
		double ox = other.x - x;
		double oz = other.z - z;
		double slope = ox/oz;
		double angle = Math.atan(slope) * degrees;
		if(angle < 0) angle += 360;
		angle += 90;
		if(angle >= 360) angle -= 360;
		double adjustedYaw = yaw;
		if(angle > 270 && yaw < 90) adjustedYaw += 360;
		return Math.abs(adjustedYaw - angle);
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
