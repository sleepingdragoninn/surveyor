package folk.sisby.surveyor;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import folk.sisby.surveyor.config.NetworkMode;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.packet.C2SKnownLandmarksPacket;
import folk.sisby.surveyor.packet.C2SKnownStructuresPacket;
import folk.sisby.surveyor.packet.C2SKnownTerrainPacket;
import folk.sisby.surveyor.packet.C2SPacket;
import folk.sisby.surveyor.packet.SyncLandmarksRequestedPacket;
import folk.sisby.surveyor.packet.S2CStructuresAddedPacket;
import folk.sisby.surveyor.packet.SyncLandmarksAddedPacket;
import folk.sisby.surveyor.packet.SyncLandmarksRemovedPacket;
import folk.sisby.surveyor.util.RegionPos;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.Structure;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public class SurveyorNetworking {

	public static Consumer<C2SPacket> C2S_SENDER = p -> {
	};

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(C2SKnownTerrainPacket.ID, (sv, p, h, b, se) -> handleServer(p, b, C2SKnownTerrainPacket::read, SurveyorNetworking::handleKnownTerrain));
		ServerPlayNetworking.registerGlobalReceiver(C2SKnownStructuresPacket.ID, (sv, p, h, b, se) -> handleServer(p, b, C2SKnownStructuresPacket::read, SurveyorNetworking::handleKnownStructures));
		ServerPlayNetworking.registerGlobalReceiver(C2SKnownLandmarksPacket.ID, (sv, p, h, b, se) -> handleServer(p, b, C2SKnownLandmarksPacket::read, SurveyorNetworking::handleKnownLandmarks));
		ServerPlayNetworking.registerGlobalReceiver(SyncLandmarksAddedPacket.ID, (sv, p, h, b, se) -> handleServer(p, b, SyncLandmarksAddedPacket::read, SurveyorNetworking::handleLandmarksAdded));
		ServerPlayNetworking.registerGlobalReceiver(SyncLandmarksRemovedPacket.ID, (sv, p, h, b, se) -> handleServer(p, b, SyncLandmarksRemovedPacket::read, SurveyorNetworking::handleLandmarksRemoved));
		ServerPlayNetworking.registerGlobalReceiver(SyncLandmarksRequestedPacket.ID, (sv, p, h, b, se) -> handleServer(p, b, SyncLandmarksRequestedPacket::read, SurveyorNetworking::handleLandmarksRequested));
	}

	private static SurveyorExploration explorationForMode(NetworkMode mode, ServerPlayerEntity player) {
		return mode.atLeast(NetworkMode.SERVER) ? null : mode.atLeast(NetworkMode.GROUP) ? SurveyorExploration.ofShared(player) : mode.atLeast(NetworkMode.SOLO) ? SurveyorExploration.of(player) : PlayerSummary.OfflinePlayerSummary.OfflinePlayerExploration.empty(player.getUuid());
	}

	private static void handleKnownTerrain(ServerPlayerEntity player, ServerWorld world, WorldSummary summary, C2SKnownTerrainPacket packet) {
		if (summary.terrain() == null || Surveyor.CONFIG.networking.terrain.atMost(NetworkMode.NONE)) return;
		Map<RegionPos, BitSet> serverBits = summary.terrain().bitSet(explorationForMode(Surveyor.CONFIG.networking.terrain, player));
		Map<RegionPos, BitSet> clientBits = packet.regionBits();
		serverBits.forEach((rPos, set) -> {
			if (clientBits.containsKey(rPos)) set.andNot(clientBits.get(rPos));
			if (!set.isEmpty()) {
				summary.terrain().queueUpdate(rPos, set, player);
			}
		});
	}

	private static void handleKnownStructures(ServerPlayerEntity player, ServerWorld world, WorldSummary summary, C2SKnownStructuresPacket packet) {
		if (summary.structures() == null || Surveyor.CONFIG.networking.structures.atMost(NetworkMode.NONE)) return;
		Multimap<RegistryKey<Structure>, ChunkPos> structures = summary.structures().keySet(explorationForMode(Surveyor.CONFIG.networking.structures, player));
		packet.structureKeys().forEach(structures::remove);
		if (structures.isEmpty()) return;
		SurveyorExploration personalExploration = SurveyorExploration.of(player);
		Multimap<RegistryKey<Structure>, ChunkPos> personalStructures = personalExploration.limitStructureKeySet(world.getRegistryKey(), HashMultimap.create(structures));
		if (!personalStructures.isEmpty()) S2CStructuresAddedPacket.of(false, personalStructures, summary.structures()).send(player);
		personalStructures.forEach(structures::remove);
		if (!structures.isEmpty()) S2CStructuresAddedPacket.of(true, structures, summary.structures()).send(player);
	}

	private static void handleKnownLandmarks(ServerPlayerEntity player, ServerWorld world, WorldSummary summary, C2SKnownLandmarksPacket packet) {
		if (summary.landmarks() == null || Surveyor.CONFIG.networking.landmarks.atMost(NetworkMode.NONE)) return;
		Multimap<UUID, Identifier> landmarks = summary.landmarks().keySet(explorationForMode(Surveyor.CONFIG.networking.landmarks, player));
		Multimap<UUID, Identifier> addLandmarks = HashMultimap.create(landmarks);
		if (!Surveyor.CONFIG.forceUpdateLandmarks) packet.landmarks().forEach(addLandmarks::remove);
		if (!addLandmarks.isEmpty()) SyncLandmarksAddedPacket.of(addLandmarks, summary.landmarks()).send(player);
		Multimap<UUID, Identifier> removeLandmarks = HashMultimap.create(packet.landmarks());
		Multimap<UUID, Identifier> removedLandmarks = summary.landmarks().removed();
		removeLandmarks.entries().removeIf(e -> !removedLandmarks.containsEntry(e.getKey(), e.getValue()));
		if (!removeLandmarks.isEmpty()) new SyncLandmarksRemovedPacket(removeLandmarks).send(player);
		Multimap<UUID, Identifier> unknownWaypoints = HashMultimap.create();
		UUID uuid = Surveyor.getUuid(player);
		unknownWaypoints.putAll(uuid, packet.landmarks().get(uuid));
		summary.landmarks().keySet(null).get(uuid).forEach(id -> unknownWaypoints.remove(uuid, id));
		removedLandmarks.get(uuid).forEach(id -> unknownWaypoints.remove(uuid, id));
		if (!unknownWaypoints.isEmpty()) new SyncLandmarksRequestedPacket(unknownWaypoints).send(player);
	}

	private static void handleLandmarksAdded(ServerPlayerEntity player, ServerWorld world, WorldSummary summary, SyncLandmarksAddedPacket packet) {
		if (summary.landmarks() == null) return;
		summary.landmarks().readUpdatePacket(world, packet, player);
	}

	private static void handleLandmarksRemoved(ServerPlayerEntity player, ServerWorld world, WorldSummary summary, SyncLandmarksRemovedPacket packet) {
		if (summary.landmarks() == null) return;
		Map<UUID, Map<Identifier, Landmark>> changed = new HashMap<>();
		packet.landmarks().forEach((uuid, id) -> {
			if (summary.landmarks().contains(uuid, id) && Surveyor.getUuid(player).equals(summary.landmarks().get(uuid, id).owner())) summary.landmarks().removeForBatch(changed, uuid, id);
		});
		if (!changed.isEmpty()) summary.landmarks().handleChanged(world, changed, false, player);
	}

	private static void handleLandmarksRequested(ServerPlayerEntity player, ServerWorld world, WorldSummary summary, SyncLandmarksRequestedPacket packet) {
		if (summary.landmarks() == null) return;
		Multimap<UUID, Identifier> allowed = SurveyorExploration.ofShared(player).limitLandmarkKeySet(world.getRegistryKey(), summary.landmarks(), HashMultimap.create(packet.landmarks()));
		summary.landmarks().createUpdatePacket(allowed).send(player);
	}

	private static <T extends C2SPacket> void handleServer(ServerPlayerEntity player, PacketByteBuf buf, Function<PacketByteBuf, T> reader, ServerPacketHandler<T> handler) {
		T packet = reader.apply(buf);
		handler.handle(player, player.getServerWorld(), WorldSummary.of(player.getServerWorld()), packet);
	}

	public interface ServerPacketHandler<T> {
		void handle(ServerPlayerEntity player, ServerWorld world, WorldSummary summary, T packet);
	}
}
