package folk.sisby.surveyor.packet;

import folk.sisby.surveyor.ServerSummary;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.config.NetworkMode;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface S2CPacket extends SurveyorPacket {
	default void send(Collection<ServerPlayerEntity> players) {
		if (players.isEmpty()) return;
		List<SurveyorPacket> split = this.toPayloads(players.stream().findFirst().orElseThrow().getRegistryManager());
		if (split.isEmpty()) return;
		for (ServerPlayerEntity player : players) {
			if (!ServerPlayNetworking.canSend(player, getId()) || player.getServer().isHost(player.getGameProfile())) continue;
			split.forEach(p -> ServerPlayNetworking.send(player, p));
		}
	}

	default void send(ServerPlayerEntity player) {
		send(List.of(player));
	}

	default void send(ServerWorld world) {
		send(world.getPlayers());
	}

	default void send(ServerPlayerEntity sender, ServerWorld world) {
		List<ServerPlayerEntity> players = new ArrayList<>(world.getPlayers());
		players.remove(sender);
		send(players);
	}

	default void send(ServerPlayerEntity sender, MinecraftServer server, Collection<ServerPlayerEntity> allPlayers, NetworkMode mode) {
		if (mode.atMost(NetworkMode.NONE) || (sender != null && mode.atMost(NetworkMode.SOLO))) return;
		List<ServerPlayerEntity> players = new ArrayList<>(allPlayers);
		players.remove(sender);
		if (sender != null && mode.atMost(NetworkMode.GROUP)) { // reduce to just group members
			Set<ServerPlayerEntity> group = ServerSummary.of(server).groupOtherServerPlayers(Surveyor.getUuid(sender), server);
			players.removeIf(p -> !group.contains(p));
		}
		send(players);
	}

	default void send(ServerPlayerEntity sender, ServerWorld world, NetworkMode mode) {
		send(sender, world.getServer(), world.getPlayers(), mode);
	}

	default void send(MinecraftServer server) {
		send(server.getPlayerManager().getPlayerList());
	}

	default void send(ServerPlayerEntity sender, MinecraftServer server) {
		List<ServerPlayerEntity> players = new ArrayList<>(server.getPlayerManager().getPlayerList());
		players.remove(sender);
		send(players);
	}
}
