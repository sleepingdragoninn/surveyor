package folk.sisby.surveyor.packet;

import folk.sisby.surveyor.ServerSummary;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.config.NetworkMode;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface S2CPacket extends SurveyorPacket {
	default void send(Collection<ServerPlayerEntity> players) {
		List<SurveyorPacket> split = this.toPayloads();
		if (split.isEmpty()) return;
		for (ServerPlayerEntity player : players) {
			if (!ServerPlayNetworking.canSend(player, getId()) || player.getServer().isHost(player.getGameProfile())) continue;
			split.forEach(p -> ServerPlayNetworking.send(player, p));
		}
	}

	default void send(ServerPlayerEntity player) {
		send(List.of(player));
	}

	default void send(UUID sender, MinecraftServer server) {
		List<ServerPlayerEntity> players = new ArrayList<>(server.getPlayerManager().getPlayerList());
		players.removeIf(p -> Surveyor.getUuid(p).equals(sender));
		send(players);
	}

	default void send(UUID sender, MinecraftServer server, Collection<ServerPlayerEntity> allPlayers, NetworkMode mode, boolean withSelf) {
		if (mode.atMost(NetworkMode.NONE) || (sender != null && mode.atMost(NetworkMode.SOLO))) return;
		List<ServerPlayerEntity> players = new ArrayList<>(allPlayers);
		if (sender != null) {
			Set<ServerPlayerEntity> group = ServerSummary.of(server).getSharingPlayers(sender, mode, withSelf);
			players.removeIf(p -> !group.contains(p));
		}
		send(players);
	}

	default void send(UUID sender, MinecraftServer server, NetworkMode mode, boolean withSelf) {
		send(sender, server, server.getPlayerManager().getPlayerList(), mode, withSelf);
	}

	default void send(MinecraftServer server) {
		send(server.getPlayerManager().getPlayerList());
	}
}
