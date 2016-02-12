package com.biggestnerd.radarjammer;

import java.util.UUID;

import org.bukkit.Location;

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
	
	public PlayerLocation(double x, double y, double z, float yaw, float pitch, UUID id, boolean invis) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.yaw = yaw;
		this.pitch = pitch;
		if(yaw < 0) yaw += 360;
		this.id = id;
		this.invis = invis;
	}
	
	public PlayerLocation(Location loc, UUID id, boolean invis) {
		this(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), loc.getYaw(), loc.getPitch(), id, invis);
	}
	
	public double getAngle(PlayerLocation other) {
		double xz = Math.cos(pitch * radians);
		double vX = (-xz * Math.sin(yaw * radians));
		double vZ = (xz * Math.cos(yaw * radians));
		Vector vec1 = new Vector(vX, vZ, -Math.sin(pitch * radians));
		Vector vec2 = new Vector(other.x - x, other.z - z, other.y - y);
		return vec1.angle(vec2);
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
	
	public class Vector {

		private double x;
		private double z;
		private double y;
		
		public Vector(double x, double z, double y) {
			this.x = x;
			this.z = z;
			this.y = y;
		}

		public double angle(Vector other) {
			double dot = dot(other) / (length() * other.length());
			return Math.acos(dot) * degrees;
		}
		
		public double dot(Vector other) {
			return x * other.x + y * other.y + z * other.z;
		}
		
		public double length() {
			return Math.sqrt((x * x) + (z * z) + (y * y));
		}
	}
}
