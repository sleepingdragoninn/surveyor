package folk.sisby.surveyor;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import folk.sisby.surveyor.config.NetworkMode;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.packet.C2SKnownLandmarksPacket;
import folk.sisby.surveyor.packet.C2SKnownStructuresPacket;
import folk.sisby.surveyor.packet.C2SKnownTerrainPacket;
import folk.sisby.surveyor.packet.C2SPacket;
import folk.sisby.surveyor.packet.S2CGroupAmendedPacket;
import folk.sisby.surveyor.packet.S2CUpdateRegionPacket;
import folk.sisby.surveyor.packet.SyncLandmarksRequestedPacket;
import folk.sisby.surveyor.packet.S2CGroupChangedPacket;
import folk.sisby.surveyor.packet.S2CGroupUpdatedPacket;
import folk.sisby.surveyor.packet.S2CStructuresAddedPacket;
import folk.sisby.surveyor.packet.SyncLandmarksAddedPacket;
import folk.sisby.surveyor.packet.SyncLandmarksRemovedPacket;
import folk.sisby.surveyor.util.MapUtil;
import folk.sisby.surveyor.util.RegionPos;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.Structure;

import java.util.BitSet;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class SurveyorNetworking {

	public static BiConsumer<DynamicRegistryManager, C2SPacket> C2S_SENDER = (r, p) -> {
	};

	public static void init() {
		PayloadTypeRegistry.playC2S().register(C2SKnownTerrainPacket.ID, C2SKnownTerrainPacket.CODEC);
		PayloadTypeRegistry.playC2S().register(C2SKnownStructuresPacket.ID, C2SKnownStructuresPacket.CODEC);
		PayloadTypeRegistry.playC2S().register(C2SKnownLandmarksPacket.ID, C2SKnownLandmarksPacket.CODEC);
		PayloadTypeRegistry.playC2S().register(SyncLandmarksAddedPacket.ID, SyncLandmarksAddedPacket.CODEC);
		PayloadTypeRegistry.playC2S().register(SyncLandmarksRemovedPacket.ID, SyncLandmarksRemovedPacket.CODEC);
		PayloadTypeRegistry.playC2S().register(SyncLandmarksRequestedPacket.ID, SyncLandmarksRequestedPacket.CODEC);

		PayloadTypeRegistry.playS2C().register(S2CUpdateRegionPacket.ID, S2CUpdateRegionPacket.CODEC);
		PayloadTypeRegistry.playS2C().register(S2CStructuresAddedPacket.ID, S2CStructuresAddedPacket.CODEC);
		PayloadTypeRegistry.playS2C().register(S2CGroupChangedPacket.ID, S2CGroupChangedPacket.CODEC);
		PayloadTypeRegistry.playS2C().register(S2CGroupAmendedPacket.ID, S2CGroupAmendedPacket.CODEC);
		PayloadTypeRegistry.playS2C().register(S2CGroupUpdatedPacket.ID, S2CGroupUpdatedPacket.CODEC);
		PayloadTypeRegistry.playS2C().register(SyncLandmarksAddedPacket.ID, SyncLandmarksAddedPacket.CODEC);
		PayloadTypeRegistry.playS2C().register(SyncLandmarksRemovedPacket.ID, SyncLandmarksRemovedPacket.CODEC);
		PayloadTypeRegistry.playS2C().register(SyncLandmarksRequestedPacket.ID, SyncLandmarksRequestedPacket.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(C2SKnownTerrainPacket.ID, (packet, context) -> handleServer(packet, context, SurveyorNetworking::handleKnownTerrain));
		ServerPlayNetworking.registerGlobalReceiver(C2SKnownStructuresPacket.ID, (packet, context) -> handleServer(packet, context, SurveyorNetworking::handleKnownStructures));
		ServerPlayNetworking.registerGlobalReceiver(C2SKnownLandmarksPacket.ID, (packet, context) -> handleServer(packet, context, SurveyorNetworking::handleKnownLandmarks));
		ServerPlayNetworking.registerGlobalReceiver(SyncLandmarksAddedPacket.ID, (packet, context) -> handleServer(packet, context, SurveyorNetworking::handleLandmarksAdded));
		ServerPlayNetworking.registerGlobalReceiver(SyncLandmarksRemovedPacket.ID, (packet, context) -> handleServer(packet, context, SurveyorNetworking::handleLandmarksRemoved));
		ServerPlayNetworking.registerGlobalReceiver(SyncLandmarksRequestedPacket.ID, (packet, context) -> handleServer(packet, context, SurveyorNetworking::handleLandmarksRequested));
	}

	private static void handleKnownTerrain(ServerPlayerEntity player, ServerWorld world, WorldSummary summary, C2SKnownTerrainPacket packet) {
		if (summary.terrain() == null || Surveyor.CONFIG.networking.terrain.atMost(NetworkMode.NONE)) return;
		Map<RegionPos, BitSet> serverBits = summary.terrain().bitSet(Surveyor.explorationForMode(Surveyor.CONFIG.networking.terrain, player));
		Map<RegionPos, BitSet> clientBits = packet.regionBits();
		int regions = 0;
		int chunks = 0;
		for (RegionPos rPos : serverBits.keySet()) {
			BitSet set = serverBits.get(rPos);
			if (clientBits.containsKey(rPos)) set.andNot(clientBits.get(rPos));
			if (!set.isEmpty()) {
				regions++;
				chunks += set.cardinality();
				summary.terrain().queueUpdate(world, rPos, set, player);
			}
		}
		if (regions > 0) Surveyor.LOGGER.info("[Surveyor] Syncing {} missing chunks over {} regions to player {}.", chunks, regions, player.getGameProfile().getName());
	}

	private static void handleKnownStructures(ServerPlayerEntity player, ServerWorld world, WorldSummary summary, C2SKnownStructuresPacket packet) {
		if (summary.structures() == null || Surveyor.CONFIG.networking.structures.atMost(NetworkMode.NONE)) return;
		Multimap<RegistryKey<Structure>, ChunkPos> structures = summary.structures().keySet(Surveyor.explorationForMode(Surveyor.CONFIG.networking.structures, player));
		packet.structureKeys().forEach(structures::remove);
		if (structures.isEmpty()) return;
		SurveyorExploration personalExploration = SurveyorExploration.of(player);
		Multimap<RegistryKey<Structure>, ChunkPos> personalStructures = personalExploration.limitStructureKeySet(world.getRegistryKey(), HashMultimap.create(structures));
		if (!personalStructures.isEmpty()) S2CStructuresAddedPacket.of(false, personalStructures, summary.structures()).send(player);
		personalStructures.forEach(structures::remove);
		if (!structures.isEmpty()) S2CStructuresAddedPacket.of(true, structures, summary.structures()).send(player);
		if (!personalStructures.isEmpty() || !structures.isEmpty()) Surveyor.LOGGER.info("[Surveyor] Syncing {} personal and {} shared structures to player {}", personalStructures.size(), structures.size(), player.getGameProfile().getName());
	}

	private static void handleKnownLandmarks(ServerPlayerEntity player, ServerWorld world, WorldSummary summary, C2SKnownLandmarksPacket packet) {
		if (summary.landmarks() == null || Surveyor.CONFIG.networking.landmarks.atMost(NetworkMode.NONE)) return;
		UUID uuid = Surveyor.getUuid(player);
		Multimap<UUID, Identifier> landmarks = summary.landmarks().keySet(Surveyor.explorationForMode(Surveyor.CONFIG.networking.landmarks, player));
		Multimap<UUID, Identifier> addLandmarks = HashMultimap.create(landmarks);
		if (!Surveyor.CONFIG.forceUpdateLandmarks) packet.landmarks().forEach(addLandmarks::remove);
		if (!addLandmarks.isEmpty()) SyncLandmarksAddedPacket.of(addLandmarks, summary.landmarks()).send(player);
		Multimap<UUID, Identifier> removeLandmarks = HashMultimap.create(packet.landmarks());
		Multimap<UUID, Identifier> removedLandmarks = summary.landmarks().removed();
		removeLandmarks.entries().removeIf(e -> !(removedLandmarks.containsEntry(e.getKey(), e.getValue()) || (!landmarks.containsEntry(e.getKey(), e.getValue()) && !e.getKey().equals(WorldLandmarks.GLOBAL) && !e.getKey().equals(uuid))));
		if (!removeLandmarks.isEmpty()) new SyncLandmarksRemovedPacket(removeLandmarks).send(player);
		Multimap<UUID, Identifier> unknownWaypoints = HashMultimap.create();
		unknownWaypoints.putAll(uuid, packet.landmarks().get(uuid));
		summary.landmarks().keySet(null).get(uuid).forEach(id -> unknownWaypoints.remove(uuid, id));
		removedLandmarks.get(uuid).forEach(id -> unknownWaypoints.remove(uuid, id));
		if (!unknownWaypoints.isEmpty()) new SyncLandmarksRequestedPacket(unknownWaypoints).send(player);
		if (!addLandmarks.isEmpty() || !removeLandmarks.isEmpty() || !unknownWaypoints.isEmpty()) Surveyor.LOGGER.info("[Surveyor] Syncing {} landmarks and {} removals and {} unknowns from player {}", addLandmarks.size(), removeLandmarks.size(), unknownWaypoints.size(), player.getGameProfile().getName());
	}

	private static void handleLandmarksAdded(ServerPlayerEntity player, ServerWorld world, WorldSummary summary, SyncLandmarksAddedPacket packet) {
		if (summary.landmarks() == null) return;
		Multimap<UUID, Identifier> keys = MapUtil.keyMultiMap(summary.landmarks().readUpdatePacket(world, packet, player));
		if (!keys.isEmpty()) Surveyor.LOGGER.info("[Surveyor] Adding landmark(s) from player {} - {}", player.getGameProfile().getName(), keys.values().stream().map(Identifier::toString).collect(Collectors.joining(", ")));
	}

	private static void handleLandmarksRemoved(ServerPlayerEntity player, ServerWorld world, WorldSummary summary, SyncLandmarksRemovedPacket packet) {
		if (summary.landmarks() == null) return;
		Map<UUID, Map<Identifier, Landmark>> changed = summary.landmarks().readUpdatePacket(world, packet, player);
		if (!changed.isEmpty()) {
			summary.landmarks().handleChanged(world, changed, false, player);
			Multimap<UUID, Identifier> keys = MapUtil.keyMultiMap(changed);
			Surveyor.LOGGER.info("[Surveyor] Removing landmark(s) for player {} - {}", player.getGameProfile().getName(), keys.values().stream().map(Identifier::toString).collect(Collectors.joining(", ")));
		}
	}

	private static void handleLandmarksRequested(ServerPlayerEntity player, ServerWorld world, WorldSummary summary, SyncLandmarksRequestedPacket packet) {
		if (summary.landmarks() == null) return;
		Multimap<UUID, Identifier> allowed = SurveyorExploration.ofShared(player).limitLandmarkKeySet(world.getRegistryKey(), summary.landmarks(), HashMultimap.create(packet.landmarks()));
		if (!allowed.isEmpty()) {
			summary.landmarks().createUpdatePacket(allowed).send(player);
			Surveyor.LOGGER.info("[Surveyor] Sending requested landmark(s) to player {} - {}", player.getGameProfile().getName(), allowed.values().stream().map(Identifier::toString).collect(Collectors.joining(", ")));
		}
	}

	private static <T extends C2SPacket> void handleServer(T packet, ServerPlayNetworking.Context context, ServerPacketHandler<T> handler) {
		handler.handle(context.player(), context.player().getWorld(), WorldSummary.of(context.player().getWorld()), packet);
	}

	public interface ServerPacketHandler<T> {
		void handle(ServerPlayerEntity player, ServerWorld world, WorldSummary summary, T packet);
	}
}
