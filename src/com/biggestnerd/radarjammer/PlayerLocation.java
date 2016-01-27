package com.biggestnerd.radarjammer;

import org.bukkit.Location;

public class PlayerLocation {

	public static double vFov;
	public static double hFov;
	
	private double x;
	private double y;
	private double z;
	private float yaw;
	private float pitch;
	
	public PlayerLocation(double x, double y, double z, float yaw, float pitch) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.yaw = yaw;
		this.pitch = pitch;
		if(yaw < 0) yaw += 360;
	}
	
	public PlayerLocation(Location loc) {
		this(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), loc.getYaw(), loc.getPitch());
	}
	
	public boolean canSee(PlayerLocation other) {
		PlayerLocation rightCorner = new PlayerLocation(other.x, y, other.z, 0, 0);
		double vRads = Math.atan(rightCorner.getDistance(other) / rightCorner.getDistance(this));
		double vAngle = vRads * (180 / Math.PI);
		boolean vBounds = Math.abs(vAngle - pitch) < vFov;
		
		double xz = Math.cos(Math.toRadians(pitch));
		double vX = (-xz * Math.sin(Math.toRadians(yaw)));
		double vZ = (xz * Math.cos(Math.toRadians(yaw)));
		Vector vec1 = new Vector(vX, vZ);
		Vector vec2 = new Vector(other.x - x, other.z - z);
		double hAngle = vec1.angle(vec2);
		boolean hBounds = hAngle < hFov;
		
		return vBounds && hBounds;
	}

	
	public double getDistance(PlayerLocation loc) {
		double dx = x - loc.x;
		double dy = y - loc.y;
		double dz = z - loc.z;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
	
	public class Vector {

		private double x;
		private double z;
		
		public Vector(double x, double z) {
			this.x = x;
			this.z = z;
		}

		public double angle(Vector other) {
			double dot = dot(other) / (length() * other.length());
			return Math.acos(dot) * (180 / Math.PI);
		}
		
		public double dot(Vector other) {
			return x * other.x + z * other.z;
		}
		
		public double length() {
			return Math.sqrt((x * x) + (z * z));
		}
	}
}
