package folk.sisby.surveyor.client;

import com.google.common.collect.Multimap;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.SurveyorEvents;
import folk.sisby.surveyor.SurveyorExploration;
import folk.sisby.surveyor.SurveyorNetworking;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.config.NetworkMode;
import folk.sisby.surveyor.packet.C2SKnownLandmarksPacket;
import folk.sisby.surveyor.packet.C2SKnownStructuresPacket;
import folk.sisby.surveyor.packet.C2SKnownTerrainPacket;
import folk.sisby.surveyor.packet.S2CGroupChangedPacket;
import folk.sisby.surveyor.packet.S2CGroupUpdatedPacket;
import folk.sisby.surveyor.packet.S2CPacket;
import folk.sisby.surveyor.packet.S2CStructuresAddedPacket;
import folk.sisby.surveyor.packet.S2CUpdateRegionPacket;
import folk.sisby.surveyor.packet.SyncLandmarksAddedPacket;
import folk.sisby.surveyor.packet.SyncLandmarksRemovedPacket;
import folk.sisby.surveyor.terrain.RegionSummary;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.Structure;

public class SurveyorClientNetworking {
	public static void init() {
		SurveyorNetworking.C2S_SENDER = (r, p) -> {
			if (!ClientPlayNetworking.canSend(p.getId())) return;
			p.toPayloads(r).forEach(ClientPlayNetworking::send);
		};
		ClientPlayNetworking.registerGlobalReceiver(S2CStructuresAddedPacket.ID, (packet, context) -> handleClient(packet, context, SurveyorClientNetworking::handleStructuresAdded));
		ClientPlayNetworking.registerGlobalReceiver(S2CUpdateRegionPacket.ID, (packet, context) -> handleClient(packet, context, SurveyorClientNetworking::handleTerrainAdded));
		ClientPlayNetworking.registerGlobalReceiver(S2CGroupChangedPacket.ID, (packet, context) -> handleClient(packet, context, SurveyorClientNetworking::handleGroupChanged));
		ClientPlayNetworking.registerGlobalReceiver(S2CGroupUpdatedPacket.ID, (packet, context) -> handleClient(packet, context, SurveyorClientNetworking::handleGroupUpdated));
		ClientPlayNetworking.registerGlobalReceiver(SyncLandmarksAddedPacket.ID, (packet, context) -> handleClient(packet, context, SurveyorClientNetworking::handleLandmarksAdded));
		ClientPlayNetworking.registerGlobalReceiver(SyncLandmarksRemovedPacket.ID, (packet, context) -> handleClient(packet, context, SurveyorClientNetworking::handleLandmarksRemoved));
	}

	private static void handleTerrainAdded(ClientWorld world, WorldSummary summary, S2CUpdateRegionPacket packet) {
		if (summary.terrain() == null) return;
		summary.terrain().getRegion(packet.regionPos()).readUpdatePacket(world.getRegistryManager(), packet);
		(packet.shared() ? SurveyorClient.getSharedExploration() : SurveyorClient.getPersonalExploration()).mergeRegion(world.getRegistryKey(), packet.regionPos(), packet.set());
		SurveyorEvents.Invoke.terrainUpdated(world, packet.set().stream().mapToObj(i -> RegionSummary.chunkForBit(packet.regionPos(), i)).toList());
	}

	private static void handleStructuresAdded(ClientWorld world, WorldSummary summary, S2CStructuresAddedPacket packet) {
		if (summary.structures() == null) return;
		Multimap<RegistryKey<Structure>, ChunkPos> keySet = summary.structures().readUpdatePacket(world, packet);
		if (MinecraftClient.getInstance().player != null) {
			SurveyorExploration exploration = (packet.shared() ? SurveyorClient.getSharedExploration() : SurveyorClient.getPersonalExploration());
			keySet.forEach((key, pos) -> exploration.addStructure(world.getRegistryKey(), key, pos));
		}
	}

	private static void handleGroupChanged(ClientWorld world, WorldSummary summary, S2CGroupChangedPacket packet) {
		if (!SurveyorClient.getSharedExploration().groupPlayers().equals(packet.players().keySet())) {
			SurveyorClient.getSharedExploration().groupPlayers().clear();
			SurveyorClient.getSharedExploration().groupPlayers().addAll(packet.players().keySet());
		}
		NetworkHandlerSummary.of(MinecraftClient.getInstance().getNetworkHandler()).mergeSummaries(packet.players());
		SurveyorClient.getSharedExploration().replaceTerrain(world.getRegistryKey(), packet.regionBits());
		SurveyorClient.getSharedExploration().replaceStructures(world.getRegistryKey(), packet.structureKeys());
		SurveyorClient.getExploration().updateClientForLandmarks(world);
		if (summary != null) {
			if (summary.terrain() != null && Surveyor.CONFIG.networking.terrain.atLeast(NetworkMode.SOLO)) new C2SKnownTerrainPacket(summary.terrain().bitSet(null)).send(world.getRegistryManager());
			if (summary.structures() != null && Surveyor.CONFIG.networking.structures.atLeast(NetworkMode.SOLO)) new C2SKnownStructuresPacket(summary.structures().keySet(null)).send(world.getRegistryManager());
			if (summary.landmarks() != null && Surveyor.CONFIG.networking.landmarks.atLeast(NetworkMode.SOLO)) new C2SKnownLandmarksPacket(summary.landmarks().keySet(null)).send(world.getRegistryManager());
		}
	}

	private static void handleGroupUpdated(ClientWorld world, WorldSummary summary, S2CGroupUpdatedPacket packet) {
		NetworkHandlerSummary.of(MinecraftClient.getInstance().getNetworkHandler()).mergeSummaries(packet.players());
	}

	private static void handleLandmarksAdded(ClientWorld world, WorldSummary summary, SyncLandmarksAddedPacket packet) {
		if (summary.landmarks() == null) return;
		summary.landmarks().readUpdatePacket(world, packet, null);
	}

	private static void handleLandmarksRemoved(ClientWorld world, WorldSummary summary, SyncLandmarksRemovedPacket packet) {
		if (summary.landmarks() == null) return;
		summary.landmarks().readUpdatePacket(world, packet, null);
	}

	private static <T extends S2CPacket> void handleClient(T packet, ClientPlayNetworking.Context context, ClientPacketHandler<T> handler) {
		ClientWorld world = context.client().world;
		WorldSummary summary = world == null ? null : WorldSummary.of(world);
		if (summary != null && !summary.isClient()) return;
		MinecraftClient.getInstance().execute(() -> handler.handle(world, summary, packet));
	}

	public interface ClientPacketHandler<T> {
		void handle(ClientWorld clientWorld, WorldSummary summary, T packet);
	}
}
