package folk.sisby.surveyor.client;

import com.google.common.collect.Multimap;
import folk.sisby.surveyor.PlayerSummary;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.SurveyorEvents;
import folk.sisby.surveyor.SurveyorExploration;
import folk.sisby.surveyor.SurveyorNetworking;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.config.NetworkMode;
import folk.sisby.surveyor.packet.C2SKnownLandmarksPacket;
import folk.sisby.surveyor.packet.C2SKnownStructuresPacket;
import folk.sisby.surveyor.packet.C2SKnownTerrainPacket;
import folk.sisby.surveyor.packet.S2CGroupAmendedPacket;
import folk.sisby.surveyor.packet.S2CGroupChangedPacket;
import folk.sisby.surveyor.packet.S2CGroupUpdatedPacket;
import folk.sisby.surveyor.packet.S2CPacket;
import folk.sisby.surveyor.packet.SyncLandmarksRequestedPacket;
import folk.sisby.surveyor.packet.S2CStructuresAddedPacket;
import folk.sisby.surveyor.packet.S2CUpdateRegionPacket;
import folk.sisby.surveyor.packet.SyncLandmarksAddedPacket;
import folk.sisby.surveyor.packet.SyncLandmarksRemovedPacket;
import folk.sisby.surveyor.util.MapUtil;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.Structure;

import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SurveyorClientNetworking {
	public static void init() {
		SurveyorNetworking.C2S_SENDER = p -> {
			if (!ClientPlayNetworking.canSend(p.getId())) return;
			p.toBufs().forEach(buf -> ClientPlayNetworking.send(p.getId(), buf));
		};
		ClientPlayNetworking.registerGlobalReceiver(S2CUpdateRegionPacket.ID, (c, h, b, s) -> handleClient(b, S2CUpdateRegionPacket::read, SurveyorClientNetworking::handleTerrainAdded));
		ClientPlayNetworking.registerGlobalReceiver(S2CStructuresAddedPacket.ID, (c, h, b, s) -> handleClient(b, S2CStructuresAddedPacket::read, SurveyorClientNetworking::handleStructuresAdded));
		ClientPlayNetworking.registerGlobalReceiver(S2CGroupChangedPacket.ID, (c, h, b, s) -> handleClient(b, S2CGroupChangedPacket::read, SurveyorClientNetworking::handleGroupChanged));
		ClientPlayNetworking.registerGlobalReceiver(S2CGroupAmendedPacket.ID, (c, h, b, s) -> handleClient(b, S2CGroupAmendedPacket::read, SurveyorClientNetworking::handleGroupAmended));
		ClientPlayNetworking.registerGlobalReceiver(S2CGroupUpdatedPacket.ID, (c, h, b, s) -> handleClient(b, S2CGroupUpdatedPacket::read, SurveyorClientNetworking::handleGroupUpdated));
		ClientPlayNetworking.registerGlobalReceiver(SyncLandmarksAddedPacket.ID, (c, h, b, s) -> handleClient(b, SyncLandmarksAddedPacket::read, SurveyorClientNetworking::handleLandmarksAdded));
		ClientPlayNetworking.registerGlobalReceiver(SyncLandmarksRemovedPacket.ID, (c, h, b, s) -> handleClient(b, SyncLandmarksRemovedPacket::read, SurveyorClientNetworking::handleLandmarksRemoved));
		ClientPlayNetworking.registerGlobalReceiver(SyncLandmarksRequestedPacket.ID, (c, h, b, s) -> handleClient(b, SyncLandmarksRequestedPacket::read, SurveyorClientNetworking::handleLandmarksRequested));
	}

	private static void handleTerrainAdded(ClientWorld world, WorldSummary summary, S2CUpdateRegionPacket packet) {
		if (summary.terrain() == null) return;
		summary.terrain().getRegion(packet.regionPos()).readUpdatePacket(world.getRegistryManager(), packet);
		(packet.shared() ? SurveyorClient.getSharedExploration() : SurveyorClient.getPersonalExploration()).mergeRegion(world.getRegistryKey(), packet.regionPos(), packet.set());
		SurveyorEvents.Invoke.terrainUpdated(world, packet.set().stream().mapToObj(i -> packet.regionPos().toChunk(i)).toList());
		if (packet.chunks().size() > 1) Surveyor.LOGGER.info("[Surveyor] Received {} chunks in {} from the server.", packet.chunks().size(), packet.regionPos());
	}

	private static void handleStructuresAdded(ClientWorld world, WorldSummary summary, S2CStructuresAddedPacket packet) {
		if (summary.structures() == null) return;
		Multimap<RegistryKey<Structure>, ChunkPos> keySet = summary.structures().readUpdatePacket(world, packet);
		if (MinecraftClient.getInstance().player != null && !keySet.isEmpty()) {
			SurveyorExploration exploration = (packet.shared() ? SurveyorClient.getSharedExploration() : SurveyorClient.getPersonalExploration());
			keySet.forEach((key, pos) -> exploration.addStructure(world.getRegistryKey(), key, pos));
			Surveyor.LOGGER.info("[Surveyor] Received {} structures from the server - {}", keySet.size(), keySet.keySet().stream().map(r -> r.getValue().toString()).collect(Collectors.joining(", ")));
		}
	}

	private static void handleGroupChanged(ClientWorld world, WorldSummary summary, S2CGroupChangedPacket packet) {
		if (!SurveyorClient.getSharedExploration().groupPlayers().equals(packet.players().keySet())) {
			SurveyorClient.getSharedExploration().groupPlayers().clear();
			SurveyorClient.getSharedExploration().groupPlayers().addAll(packet.players().keySet());
		}
		NetworkHandlerSummary.of(MinecraftClient.getInstance().getNetworkHandler()).matchSummaries(packet.players());
		SurveyorClient.getSharedExploration().replaceTerrain(world.getRegistryKey(), packet.regionBits());
		SurveyorClient.getSharedExploration().replaceStructures(world.getRegistryKey(), packet.structureKeys());
		SurveyorClient.getExploration().updateClientForLandmarks(world);
		if (summary != null) {
			if (summary.terrain() != null && Surveyor.CONFIG.networking.terrain.atLeast(NetworkMode.SOLO)) new C2SKnownTerrainPacket(summary.terrain().bitSet(null)).send();
			if (summary.structures() != null && Surveyor.CONFIG.networking.structures.atLeast(NetworkMode.SOLO)) new C2SKnownStructuresPacket(summary.structures().keySet(null)).send();
			if (summary.landmarks() != null && Surveyor.CONFIG.networking.landmarks.atLeast(NetworkMode.SOLO)) new C2SKnownLandmarksPacket(summary.landmarks().keySet(null)).send();
			Surveyor.LOGGER.info("[Surveyor] Received updated share group of {} from the server - {}", packet.players().size(), packet.players().values().stream().map(PlayerSummary::username).collect(Collectors.joining(", ")));
		}
	}

	private static void handleGroupAmended(ClientWorld world, WorldSummary summary, S2CGroupAmendedPacket packet) {
		SurveyorClient.getSharedExploration().groupPlayers().add(packet.player());
		PlayerEntity player = world.getPlayerByUuid(packet.player());
		Surveyor.LOGGER.info("[Surveyor] Received additional share group player {}", player == null ? packet.player() : player.getGameProfile().getName());
	}

	private static void handleGroupUpdated(ClientWorld world, WorldSummary summary, S2CGroupUpdatedPacket packet) {
		NetworkHandlerSummary.of(MinecraftClient.getInstance().getNetworkHandler()).mergeSummaries(packet.players());
	}

	private static void handleLandmarksAdded(ClientWorld world, WorldSummary summary, SyncLandmarksAddedPacket packet) {
		if (summary.landmarks() == null) return;
		summary.landmarks().readUpdatePacket(world, packet, null);
		Multimap<UUID, Identifier> keys = MapUtil.keyMultiMap(packet.landmarks());
		Surveyor.LOGGER.info("[Surveyor] Received {} landmarks from the server - {}", keys.size(), keys.values().stream().map(Identifier::toString).collect(Collectors.joining(", ")));
	}

	private static void handleLandmarksRemoved(ClientWorld world, WorldSummary summary, SyncLandmarksRemovedPacket packet) {
		if (summary.landmarks() == null) return;
		summary.landmarks().readUpdatePacket(world, packet, null);
		Surveyor.LOGGER.info("[Surveyor] Received {} landmark removals from the server - {}", packet.landmarks().size(), packet.landmarks().values().stream().map(Identifier::toString).collect(Collectors.joining(", ")));
	}

	private static void handleLandmarksRequested(ClientWorld world, WorldSummary summary, SyncLandmarksRequestedPacket packet) {
		if (summary.landmarks() == null) return;
		summary.landmarks().createUpdatePacket(packet.landmarks()).send();
		Surveyor.LOGGER.info("[Surveyor] Received {} landmark requests from the server - {}", packet.landmarks().size(), packet.landmarks().values().stream().map(Identifier::toString).collect(Collectors.joining(", ")));
	}

	private static <T extends S2CPacket> void handleClient(PacketByteBuf buf, Function<PacketByteBuf, T> reader, ClientPacketHandler<T> handler) {
		T packet = reader.apply(buf);
		WorldSummary summary = MinecraftClient.getInstance().world == null ? null : WorldSummary.of(MinecraftClient.getInstance().world);
		if (summary != null && !summary.isClient()) return;
		MinecraftClient.getInstance().execute(() -> handler.handle(MinecraftClient.getInstance().world, summary, packet));
	}

	public interface ClientPacketHandler<T> {
		void handle(ClientWorld clientWorld, WorldSummary summary, T packet);
	}
}
