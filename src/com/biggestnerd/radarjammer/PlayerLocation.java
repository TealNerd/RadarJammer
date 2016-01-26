package com.biggestnerd.radarjammer;

import org.bukkit.Location;

public class PlayerLocation {

	public static double vFov;
	public static double hFov;
	
	private final int x;
	private final int y;
	private final int z;
	private final float yaw;
	private final float pitch;
	
	public PlayerLocation(int x, int y, int z, float yaw, float pitch) {
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

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	public int getZ() {
		return z;
	}
	
	public float getYaw() {
		return yaw;
	}
	
	public float getPitch() {
		return pitch;
	}
	
	public boolean canSee(PlayerLocation loc) {
		PlayerLocation rightCorner = new PlayerLocation(loc.x, y, loc.z, 0, 0);
		double vRads = Math.atan(rightCorner.getDistance(loc) / rightCorner.getDistance(this));
		double vAngle = vRads * (180 / Math.PI);
		boolean vBounds = Math.abs(vAngle - pitch) < vFov;
		
		PlayerLocation farCorner = new PlayerLocation(x + 10, y, z, 0, 0);
		PlayerLocation adjusted = new PlayerLocation(loc.x, y, loc.z, 0, 0);
		double a = farCorner.getDistance(adjusted);
		double b = farCorner.getDistance(this);
		double c = adjusted.getDistance(this);
		double cosA = (-(a * a) + (b * b) + (c * c)) / (2 * a * c);
		double hRads = Math.acos(cosA);
		double hAngle = hRads * (180 / Math.PI);
		double realYaw = (yaw + 90) % 360;
		if(realYaw < 0) realYaw += 360.0;
		if(hAngle < 0) hAngle += 360.0;
		System.out.println(hAngle + " : " + realYaw + " : " + yaw);
		boolean hBounds = Math.abs(hAngle - realYaw) < hFov;
		
		return vBounds && hBounds;
	}
	
	public double getDistance(PlayerLocation loc) {
		int dx = x - loc.x;
		int dy = y - loc.y;
		int dz = z - loc.z;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
	
	public static void setFov(double vertFov, double horFov) {
		vertFov = vFov;
		horFov = hFov;
	}
}
