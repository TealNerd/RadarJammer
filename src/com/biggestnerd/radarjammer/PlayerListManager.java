package com.biggestnerd.radarjammer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers.PlayerInfoAction;

public class PlayerListManager extends PacketAdapter {

	public PlayerListManager(RadarJammer plugin) {
		super(plugin, ListenerPriority.NORMAL, PacketType.Play.Server.PLAYER_INFO);
	}
	
	@Override
	public void onPacketSending(PacketEvent event) {
		if(event.getPacketType() == PacketType.Play.Server.PLAYER_INFO) {
			PacketContainer packet = event.getPacket();
			PlayerInfoAction action = packet.getPlayerInfoAction().read(0);
			Player player = Bukkit.getPlayer(packet.getPlayerInfoDataLists().read(0).get(0).getProfile().getUUID());
			boolean online = player != null && player.isOnline();
			if(online && action == PlayerInfoAction.REMOVE_PLAYER) event.setCancelled(true);
		}
	}
}
