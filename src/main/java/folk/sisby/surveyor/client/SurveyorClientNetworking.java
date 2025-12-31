package folk.sisby.surveyor.client;

import com.google.common.collect.Multimap;
import folk.sisby.surveyor.PlayerSummary;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.SurveyorExploration;
import folk.sisby.surveyor.SurveyorNetworking;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.packet.S2CGroupAmendedPacket;
import folk.sisby.surveyor.packet.S2CGroupChangedPacket;
import folk.sisby.surveyor.packet.S2CGroupUpdatedPacket;
import folk.sisby.surveyor.packet.S2CPacket;
import folk.sisby.surveyor.packet.S2CStructuresAddedPacket;
import folk.sisby.surveyor.packet.S2CUpdateRegionPacket;
import folk.sisby.surveyor.packet.SyncLandmarksAddedPacket;
import folk.sisby.surveyor.packet.SyncLandmarksRemovedPacket;
import folk.sisby.surveyor.packet.SyncLandmarksRequestedPacket;
import folk.sisby.surveyor.structure.WorldStructures;
import folk.sisby.surveyor.terrain.WorldTerrain;
import folk.sisby.surveyor.util.MapUtil;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.Structure;

import java.util.BitSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SurveyorClientNetworking {
	public static void init() {
		SurveyorNetworking.C2S_SENDER = p -> {
			if (!ClientPlayNetworking.canSend(p.getId())) return;
			p.toBufs().forEach(buf -> ClientPlayNetworking.send(p.getId(), buf));
		};
		ClientPlayNetworking.registerGlobalReceiver(S2CUpdateRegionPacket.ID, (c, h, b, s) -> handleClient(b, h, S2CUpdateRegionPacket::read, SurveyorClientNetworking::handleTerrainAdded));
		ClientPlayNetworking.registerGlobalReceiver(S2CStructuresAddedPacket.ID, (c, h, b, s) -> handleClient(b, h, S2CStructuresAddedPacket::read, SurveyorClientNetworking::handleStructuresAdded));
		ClientPlayNetworking.registerGlobalReceiver(S2CGroupChangedPacket.ID, (c, h, b, s) -> handleClient(b, h, S2CGroupChangedPacket::read, SurveyorClientNetworking::handleGroupChanged));
		ClientPlayNetworking.registerGlobalReceiver(S2CGroupAmendedPacket.ID, (c, h, b, s) -> handleClient(b, h, S2CGroupAmendedPacket::read, SurveyorClientNetworking::handleGroupAmended));
		ClientPlayNetworking.registerGlobalReceiver(S2CGroupUpdatedPacket.ID, (c, h, b, s) -> handleClient(b, h, S2CGroupUpdatedPacket::read, SurveyorClientNetworking::handleGroupUpdated));
		ClientPlayNetworking.registerGlobalReceiver(SyncLandmarksAddedPacket.ID, (c, h, b, s) -> handleClient(b, h, SyncLandmarksAddedPacket::read, SurveyorClientNetworking::handleLandmarksAdded));
		ClientPlayNetworking.registerGlobalReceiver(SyncLandmarksRemovedPacket.ID, (c, h, b, s) -> handleClient(b, h, SyncLandmarksRemovedPacket::read, SurveyorClientNetworking::handleLandmarksRemoved));
		ClientPlayNetworking.registerGlobalReceiver(SyncLandmarksRequestedPacket.ID, (c, h, b, s) -> handleClient(b, h, SyncLandmarksRequestedPacket::read, SurveyorClientNetworking::handleLandmarksRequested));
	}

	private static void handleTerrainAdded(ClientPlayNetworkHandler handler, S2CUpdateRegionPacket packet) {
		WorldSummary summary = SurveyorClient.getSummary(packet.dimension(), handler);
		WorldTerrain terrain = summary == null ? null : summary.terrain();
		if (terrain == null) return;
		BitSet changed = terrain.getRegion(packet.regionPos()).readUpdatePacket(packet);
		(packet.shared() ? SurveyorClient.getSharedExploration() : SurveyorClient.getPersonalExploration()).mergeRegion(packet.dimension(), packet.regionPos(), packet.set());
		if (changed.cardinality() > 1) {
			Surveyor.LOGGER.info("[Surveyor] Received {} chunks in {} from the server.", changed.cardinality(), packet.regionPos());
		}
	}

	private static void handleStructuresAdded(ClientPlayNetworkHandler handler, S2CStructuresAddedPacket packet) {
		WorldSummary summary = SurveyorClient.getSummary(packet.dimension(), handler);
		WorldStructures structures = summary == null ? null : summary.structures();
		if (structures == null) return;
		Multimap<RegistryKey<Structure>, ChunkPos> starts = structures.readUpdatePacket(packet);
		if (MinecraftClient.getInstance().player != null && !starts.isEmpty()) {
			SurveyorExploration exploration = (packet.shared() ? SurveyorClient.getSharedExploration() : SurveyorClient.getPersonalExploration());
			starts.forEach((key, pos) -> exploration.addStructure(summary.dimension(), key, pos));
			Surveyor.LOGGER.info("[Surveyor] Received {} structures from the server - {}", starts.size(), starts.keySet().stream().map(r -> r.getValue().toString()).collect(Collectors.joining(", ")));
		}
	}

	private static void handleGroupChanged(ClientPlayNetworkHandler handler, S2CGroupChangedPacket packet) {
		if (!SurveyorClient.getSharedExploration().groupPlayers().equals(packet.players().keySet())) {
			SurveyorClient.getSharedExploration().groupPlayers().clear();
			SurveyorClient.getSharedExploration().groupPlayers().addAll(packet.players().keySet());
		}
		ClientSummary.of(MinecraftClient.getInstance().getNetworkHandler()).matchSummaries(packet.players());
		SurveyorClient.getSharedExploration().replaceTerrain(packet.chunks());
		SurveyorClient.getSharedExploration().replaceStructures(packet.starts());
		SurveyorClient.getSummaries(handler).values().forEach(summary -> SurveyorClient.getSharedExploration().updateClientForLandmarks(summary));
		SurveyorClient.sendKnownData(handler);
		Surveyor.LOGGER.info("[Surveyor] Received updated share group of {} from the server - {}", packet.players().size(), packet.players().values().stream().map(PlayerSummary::username).collect(Collectors.joining(", ")));
	}

	private static void handleGroupAmended(ClientPlayNetworkHandler handler, S2CGroupAmendedPacket packet) {
		SurveyorClient.getSharedExploration().groupPlayers().add(packet.player());
		PlayerEntity player = MinecraftClient.getInstance().world == null ? null : MinecraftClient.getInstance().world.getPlayerByUuid(packet.player());
		Surveyor.LOGGER.info("[Surveyor] Received additional share group player {}", player == null ? packet.player() : player.getGameProfile().getName());
	}

	private static void handleGroupUpdated(ClientPlayNetworkHandler handler, S2CGroupUpdatedPacket packet) {
		ClientSummary.of(MinecraftClient.getInstance().getNetworkHandler()).mergeSummaries(packet.players());
	}

	private static void handleLandmarksAdded(ClientPlayNetworkHandler handler, SyncLandmarksAddedPacket packet) {
		WorldSummary summary = SurveyorClient.getSummary(packet.dimension(), handler);
		WorldLandmarks landmarks = summary == null ? null : summary.landmarks();
		if (landmarks == null) return;
		landmarks.readUpdatePacket(packet, null);
		Multimap<UUID, Identifier> keys = MapUtil.keyMultiMap(packet.landmarks());
		Surveyor.LOGGER.info("[Surveyor] Received {} landmarks from the server - {}", keys.size(), keys.values().stream().map(Identifier::toString).collect(Collectors.joining(", ")));
	}

	private static void handleLandmarksRemoved(ClientPlayNetworkHandler handler, SyncLandmarksRemovedPacket packet) {
		WorldSummary summary = SurveyorClient.getSummary(packet.dimension(), handler);
		WorldLandmarks landmarks = summary == null ? null : summary.landmarks();
		if (landmarks == null) return;
		landmarks.readUpdatePacket(packet, null);
		Surveyor.LOGGER.info("[Surveyor] Received {} landmark removals from the server - {}", packet.landmarks().size(), packet.landmarks().values().stream().map(Identifier::toString).collect(Collectors.joining(", ")));
	}

	private static void handleLandmarksRequested(ClientPlayNetworkHandler handler, SyncLandmarksRequestedPacket packet) {
		WorldSummary summary = SurveyorClient.getSummary(packet.dimension(), handler);
		WorldLandmarks landmarks = summary == null ? null : summary.landmarks();
		if (landmarks == null) return;
		landmarks.createUpdatePacket(packet.landmarks()).send();
		Surveyor.LOGGER.info("[Surveyor] Received {} landmark requests from the server - {}", packet.landmarks().size(), packet.landmarks().values().stream().map(Identifier::toString).collect(Collectors.joining(", ")));
	}

	private static <T extends S2CPacket> void handleClient(PacketByteBuf buf, ClientPlayNetworkHandler handler, Function<PacketByteBuf, T> reader, ClientPacketHandler<T> packetHandler) {
		T packet = reader.apply(buf);
		WorldSummary summary = MinecraftClient.getInstance().world == null ? null : WorldSummary.of(MinecraftClient.getInstance().world);
		if (summary != null && !summary.isClient()) return;
		MinecraftClient.getInstance().execute(() -> packetHandler.handle(handler, packet));
	}

	public interface ClientPacketHandler<T> {
		void handle(ClientPlayNetworkHandler handler, T packet);
	}
}
