package com.biggestnerd.radarjammer.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;

public class WrapperPlayServerMount extends AbstractPacket {
	public static final PacketType TYPE = PacketType.Play.Server.MOUNT;
	
	public WrapperPlayServerMount() {
		super(new PacketContainer(TYPE), TYPE);
		handle.getModifier().writeDefaults();
	}
	
	public WrapperPlayServerMount(PacketContainer packet) {
		super(packet, TYPE);
	}
	
	public int getVehicleEID() {
		return handle.getIntegers().read(0);
	}
	
	public int getPassengerCount() {
		return handle.getIntegers().read(1);
	}
	
	public int[] getPassengerEIDs() {
		return handle.getIntegerArrays().read(0);
	}
	
	public void setVehicleEID(int id) {
		handle.getIntegers().write(0, id);
	}
	
	public void setPassengerEIDs(int... ids) {
		handle.getIntegers().write(1, ids.length);
		handle.getIntegerArrays().write(0, ids);
	}
}
