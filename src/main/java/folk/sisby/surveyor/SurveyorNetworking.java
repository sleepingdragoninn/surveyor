package folk.sisby.surveyor;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import folk.sisby.surveyor.config.NetworkMode;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.LandmarkType;
import folk.sisby.surveyor.packet.C2SKnownLandmarksPacket;
import folk.sisby.surveyor.packet.C2SKnownStructuresPacket;
import folk.sisby.surveyor.packet.C2SKnownTerrainPacket;
import folk.sisby.surveyor.packet.C2SPacket;
import folk.sisby.surveyor.packet.S2CGroupChangedPacket;
import folk.sisby.surveyor.packet.S2CGroupUpdatedPacket;
import folk.sisby.surveyor.packet.S2CStructuresAddedPacket;
import folk.sisby.surveyor.packet.S2CUpdateRegionPacket;
import folk.sisby.surveyor.packet.SyncLandmarksAddedPacket;
import folk.sisby.surveyor.packet.SyncLandmarksRemovedPacket;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.Structure;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class SurveyorNetworking {

	public static Consumer<C2SPacket> C2S_SENDER = p -> {
	};

	public static void init() {
		PayloadTypeRegistry.playC2S().register(C2SKnownTerrainPacket.ID, C2SKnownTerrainPacket.CODEC);
		PayloadTypeRegistry.playC2S().register(C2SKnownStructuresPacket.ID, C2SKnownStructuresPacket.CODEC);
		PayloadTypeRegistry.playC2S().register(C2SKnownLandmarksPacket.ID, C2SKnownLandmarksPacket.CODEC);
		PayloadTypeRegistry.playC2S().register(SyncLandmarksAddedPacket.ID, SyncLandmarksAddedPacket.CODEC);
		PayloadTypeRegistry.playC2S().register(SyncLandmarksRemovedPacket.ID, SyncLandmarksRemovedPacket.CODEC);

		PayloadTypeRegistry.playS2C().register(S2CUpdateRegionPacket.ID, S2CUpdateRegionPacket.CODEC);
		PayloadTypeRegistry.playS2C().register(S2CStructuresAddedPacket.ID, S2CStructuresAddedPacket.CODEC);
		PayloadTypeRegistry.playS2C().register(S2CGroupChangedPacket.ID, S2CGroupChangedPacket.CODEC);
		PayloadTypeRegistry.playS2C().register(S2CGroupUpdatedPacket.ID, S2CGroupUpdatedPacket.CODEC);
		PayloadTypeRegistry.playS2C().register(SyncLandmarksAddedPacket.ID, SyncLandmarksAddedPacket.CODEC);
		PayloadTypeRegistry.playS2C().register(SyncLandmarksRemovedPacket.ID, SyncLandmarksRemovedPacket.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(C2SKnownTerrainPacket.ID, (packet, context) -> handleServer(packet, context, SurveyorNetworking::handleKnownTerrain));
		ServerPlayNetworking.registerGlobalReceiver(C2SKnownStructuresPacket.ID, (packet, context) -> handleServer(packet, context, SurveyorNetworking::handleKnownStructures));
		ServerPlayNetworking.registerGlobalReceiver(C2SKnownLandmarksPacket.ID, (packet, context) -> handleServer(packet, context, SurveyorNetworking::handleKnownLandmarks));
		ServerPlayNetworking.registerGlobalReceiver(SyncLandmarksAddedPacket.ID, (packet, context) -> handleServer(packet, context, SurveyorNetworking::handleLandmarksAdded));
		ServerPlayNetworking.registerGlobalReceiver(SyncLandmarksRemovedPacket.ID, (packet, context) -> handleServer(packet, context, SurveyorNetworking::handleLandmarksRemoved));
	}

	private static void handleKnownTerrain(ServerPlayerEntity player, ServerWorld world, WorldSummary summary, C2SKnownTerrainPacket packet) {
		if (summary.terrain() == null || Surveyor.CONFIG.networking.terrain.atMost(NetworkMode.NONE)) return;
		Map<ChunkPos, BitSet> serverBits = summary.terrain().bitSet(SurveyorExploration.ofShared(player));
		Map<ChunkPos, BitSet> clientBits = packet.regionBits();
		serverBits.forEach((rPos, set) -> {
			if (clientBits.containsKey(rPos)) set.andNot(clientBits.get(rPos));
			if (!set.isEmpty()) {
				SurveyorExploration personalExploration = SurveyorExploration.of(player);
				BitSet personalSet = personalExploration.limitTerrainBitset(world.getRegistryKey(), rPos, (BitSet) set.clone());
				if (!personalSet.isEmpty()) S2CUpdateRegionPacket.of(false, rPos, summary.terrain().getRegion(rPos), personalSet).send(player);
				set.andNot(personalSet);
				if (!set.isEmpty() && Surveyor.CONFIG.networking.terrain.atLeast(NetworkMode.GROUP)) S2CUpdateRegionPacket.of(true, rPos, summary.terrain().getRegion(rPos), set).send(player);
			}
		});
	}

	private static void handleKnownStructures(ServerPlayerEntity player, ServerWorld world, WorldSummary summary, C2SKnownStructuresPacket packet) {
		if (summary.structures() == null || Surveyor.CONFIG.networking.structures.atMost(NetworkMode.NONE)) return;
		Multimap<RegistryKey<Structure>, ChunkPos> structures = summary.structures().keySet(SurveyorExploration.ofShared(player));
		packet.structureKeys().forEach(structures::remove);
		if (structures.isEmpty()) return;
		SurveyorExploration personalExploration = SurveyorExploration.of(player);
		Multimap<RegistryKey<Structure>, ChunkPos> personalStructures = personalExploration.limitStructureKeySet(world.getRegistryKey(), HashMultimap.create(structures));
		if (!personalStructures.isEmpty()) S2CStructuresAddedPacket.of(false, personalStructures, summary.structures()).send(player);
		personalStructures.forEach(structures::remove);
		if (!structures.isEmpty() && Surveyor.CONFIG.networking.structures.atLeast(NetworkMode.GROUP)) S2CStructuresAddedPacket.of(true, structures, summary.structures()).send(player);
	}

	private static void handleKnownLandmarks(ServerPlayerEntity player, ServerWorld world, WorldSummary summary, C2SKnownLandmarksPacket packet) {
		if (summary.landmarks() == null || Surveyor.CONFIG.networking.landmarks.atMost(NetworkMode.NONE)) return;
		Multimap<LandmarkType<?>, BlockPos> landmarks = summary.landmarks().keySet(Surveyor.CONFIG.networking.landmarks.atLeast(NetworkMode.GROUP) ? SurveyorExploration.ofShared(player) : SurveyorExploration.of(player));
		Multimap<LandmarkType<?>, BlockPos> addLandmarks = HashMultimap.create(landmarks);
		packet.landmarks().forEach(addLandmarks::remove);
		if (!addLandmarks.isEmpty()) SyncLandmarksAddedPacket.of(addLandmarks, summary.landmarks()).send(player);
		Multimap<LandmarkType<?>, BlockPos> removeLandmarks = HashMultimap.create(packet.landmarks());
		landmarks.forEach(removeLandmarks::remove);
		if (!removeLandmarks.isEmpty()) new SyncLandmarksRemovedPacket(removeLandmarks).send(player);
	}

	private static void handleLandmarksAdded(ServerPlayerEntity player, ServerWorld world, WorldSummary summary, SyncLandmarksAddedPacket packet) {
		if (summary.landmarks() == null) return;
		summary.landmarks().readUpdatePacket(world, packet, player);
	}

	private static void handleLandmarksRemoved(ServerPlayerEntity player, ServerWorld world, WorldSummary summary, SyncLandmarksRemovedPacket packet) {
		if (summary.landmarks() == null) return;
		Map<LandmarkType<?>, Map<BlockPos, Landmark<?>>> changed = new HashMap<>();
		packet.landmarks().forEach((type, pos) -> {
			if (summary.landmarks().contains(type, pos) && Surveyor.getUuid(player).equals(summary.landmarks().get(type, pos).owner())) summary.landmarks().removeForBatch(changed, type, pos);
		});
		if (!changed.isEmpty()) summary.landmarks().handleChanged(world, changed, false, player);
	}

	private static <T extends C2SPacket> void handleServer(T packet, ServerPlayNetworking.Context context, ServerPacketHandler<T> handler) {
		handler.handle(context.player(), context.player().getWorld(), WorldSummary.of(context.player().getWorld()), packet);
	}

	public interface ServerPacketHandler<T> {
		void handle(ServerPlayerEntity player, ServerWorld world, WorldSummary summary, T packet);
	}
}
