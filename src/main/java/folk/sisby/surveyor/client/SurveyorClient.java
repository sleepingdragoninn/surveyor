package folk.sisby.surveyor.client;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.mojang.authlib.GameProfile;
import folk.sisby.surveyor.PlayerSummary;
import folk.sisby.surveyor.ServerSummary;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.SurveyorEvents;
import folk.sisby.surveyor.SurveyorExploration;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.config.NetworkMode;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.packet.C2SKnownLandmarksPacket;
import folk.sisby.surveyor.packet.C2SKnownStructuresPacket;
import folk.sisby.surveyor.packet.C2SKnownTerrainPacket;
import folk.sisby.surveyor.structure.WorldStructures;
import folk.sisby.surveyor.terrain.WorldTerrain;
import folk.sisby.surveyor.util.RegionPos;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.structure.Structure;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class SurveyorClient implements ClientModInitializer {
	public static final String SERVERS_FILE_NAME = "servers.txt";
	private static final Multimap<RegistryKey<World>, WorldChunk> LOADING_CHUNKS = HashMultimap.create();

	public static void clearLoadingChunks() {
		LOADING_CHUNKS.clear();
	}

	public static File getSavePath(long biomeSeed) {
		String saveFolder = String.valueOf(biomeSeed);
		Path savePath = FabricLoader.getInstance().getGameDir().resolve(Surveyor.DATA_SUBFOLDER).resolve(Surveyor.ID).resolve(saveFolder);
		savePath.toFile().mkdirs();
		File serversFile = savePath.resolve(SERVERS_FILE_NAME).toFile();
		try {
			ServerInfo info = MinecraftClient.getInstance().getCurrentServerEntry();
			if (info != null && (!serversFile.exists() || !FileUtils.readFileToString(serversFile, StandardCharsets.UTF_8).contains(info.name + "\n" + info.address))) {
				FileUtils.writeStringToFile(serversFile, info.name + "\n" + info.address + "\n", StandardCharsets.UTF_8, true);
			}
		} catch (IOException e) {
			Surveyor.LOGGER.error("[Surveyor] Error writing servers file for save {}.", savePath, e);
		}
		return savePath.toFile();
	}

	public static File getWorldSavePath(RegistryKey<World> dimension, long biomeSeed) {
		String dimNamespace = dimension.getValue().getNamespace();
		String dimPath = dimension.getValue().getPath();
		return getSavePath(biomeSeed).toPath().resolve(dimNamespace).resolve(dimPath).toFile();
	}

	public static @Nullable File getXaerosSavePath(RegistryKey<World> dimension) {
		File baseFolder = FabricLoader.getInstance().getGameDir().resolve("xaero").resolve("minimap").toFile();
		if (!baseFolder.exists()) return null;
		String id = null;
		try {
			id = MinecraftClient.getInstance().getCurrentServerEntry() != null ? MinecraftClient.getInstance().getCurrentServerEntry().address : MinecraftClient.getInstance().getServer().getSavePath(WorldSavePath.ROOT).getParent().toFile().getName();
			String sanitized = (MinecraftClient.getInstance().getCurrentServerEntry() != null ? "Multiplayer_" : "") + (id.contains(":") ? id.substring(0, id.indexOf(":")) : id).replace("_", "%us%").replace("\\", "%bs%").replace("/", "%fs%").replace(":", "§").replace("[", "%lb%").replace("]", "%rb%");
			File saveFolder = baseFolder.toPath().resolve(sanitized).toFile();
			if (!saveFolder.exists()) return null;
			String sanitizedDim = dimension == World.OVERWORLD ? "dim%0" : dimension == World.NETHER ? "dim%-1" : dimension == World.END ? "dim%1" : "dim%" + dimension.toString().replace(":", "$").replace('/', '%');
			File dimFolder = saveFolder.toPath().resolve(sanitizedDim).toFile();
			if (!dimFolder.exists() || dimFolder.toPath().resolve(".surveyor_migrated").toFile().exists()) return null;
			return dimFolder;
		} catch (Exception e) {
			Surveyor.LOGGER.error("[Surveyor] Error fetching xaeros data for {} {}", id,  dimension, e);
		}
		return null;
	}

	public static boolean serverSupported() {
		return ClientPlayNetworking.canSend(C2SKnownTerrainPacket.ID);
	}

	public static Map<UUID, PlayerSummary> getFriends() {
		if (MinecraftClient.getInstance().isIntegratedServerRunning()) {
			MinecraftServer server = MinecraftClient.getInstance().getServer();
			return ServerSummary.of(server).getGroupSummaries(getClientUuid());
		} else {
			ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
			if (handler == null) return new HashMap<>();
			ClientSummary clientSummary = ClientSummary.of(handler);
			return clientSummary.players(clientSummary.shared.sharedPlayers());
		}
	}

	public static SurveyorExploration getExploration() {
		if (MinecraftClient.getInstance().isIntegratedServerRunning()) {
			return SurveyorExploration.ofShared(getClientUuid(), MinecraftClient.getInstance().getServer());
		} else {
			Set<SurveyorExploration> set = new HashSet<>();
			set.add(ClientSummary.of(MinecraftClient.getInstance().getNetworkHandler()).personal);
			set.add(ClientSummary.of(MinecraftClient.getInstance().getNetworkHandler()).shared);
			return PlayerSummary.OfflinePlayerSummary.OfflinePlayerExploration.ofMerged(set);
		}
	}

	public static SurveyorExploration getPersonalExploration() {
		if (MinecraftClient.getInstance().isIntegratedServerRunning()) {
			return SurveyorExploration.of(getClientUuid(), MinecraftClient.getInstance().getServer());
		} else {
			return ClientSummary.of(MinecraftClient.getInstance().getNetworkHandler()).personal;
		}
	}

	public static ClientExploration getSharedExploration() {
		if (MinecraftClient.getInstance().isIntegratedServerRunning()) {
			throw new IllegalStateException("You can't edit shared exploration in singleplayer!");
		} else {
			return ClientSummary.of(MinecraftClient.getInstance().getNetworkHandler()).shared;
		}
	}

	public static UUID getClientUuid() { // UUID needs to always match what the server is using.
		if (MinecraftClient.getInstance().isIntegratedServerRunning()) return ServerSummary.HOST;
		GameProfile profile = ((SurveyorNetworkHandler) MinecraftClient.getInstance().getNetworkHandler()).getProfile();
		return Uuids.getUuidFromProfile(profile);
	}

	public static ServerWorld stealServerWorld(RegistryKey<World> dimension) {
		MinecraftServer integratedServer = MinecraftClient.getInstance().getServer();
		if (integratedServer == null) return null;
		return integratedServer.getWorld(dimension);
	}

	public static Map<RegistryKey<World>, WorldSummary> getSummaries(ClientPlayNetworkHandler handler) {
		return handler.getWorldKeys().stream().collect(Collectors.toMap(k -> k, k -> getSummary(k, handler)));
	}

	public static WorldSummary getSummary(RegistryKey<World> dimension, ClientPlayNetworkHandler handler) {
		if (MinecraftClient.getInstance().isIntegratedServerRunning()) {
			return WorldSummary.of(SurveyorClient.stealServerWorld(dimension));
		} else {
			ClientSummary summary = ClientSummary.of(handler);
			return summary == null ? null : summary.getWorld(dimension);
		}
	}

	public static boolean canModify(UUID landmarkOwner) {
		if (MinecraftClient.getInstance().isIntegratedServerRunning()) return Surveyor.canModify(landmarkOwner, MinecraftClient.getInstance().getServer().getPlayerManager().getPlayer(MinecraftClient.getInstance().player.getGameProfile().getId()));
		return landmarkOwner.equals(SurveyorClient.getClientUuid()) || !landmarkOwner.equals(WorldLandmarks.GLOBAL) && (Surveyor.CONFIG.networking.waypoints.atLeast(NetworkMode.SERVER) || (Surveyor.CONFIG.networking.waypoints.atLeast(NetworkMode.GROUP) && SurveyorClient.getSharedExploration().groupPlayers().contains(landmarkOwner)));
	}

	public static @Nullable WorldSummary tryGetSummary(RegistryKey<World> dimension) {
		if (MinecraftClient.getInstance().isIntegratedServerRunning()) {
			return WorldSummary.of(SurveyorClient.stealServerWorld(dimension));
		} else {
			ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
			return handler == null ? null : getSummary(dimension, handler);
		}
	}

	public static @Nullable World getWorld(RegistryKey<World> dimension) {
		ClientWorld world = MinecraftClient.getInstance().world;
		return world != null && world.getRegistryKey().equals(dimension) ? world : null;
	}

	public static void handleInitialLoad(ClientPlayNetworkHandler handler) {
		SurveyorExploration exploration = getExploration();
		for (WorldSummary summary : SurveyorClient.getSummaries(handler).values()) {
			WorldTerrain terrain = summary.terrain();
			if (terrain != null) SurveyorClientEvents.Invoke.terrainUpdated(summary, terrain.bitSet(exploration));
			WorldStructures structures = summary.structures();
			if (structures != null) SurveyorClientEvents.Invoke.structuresAdded(summary, structures.keySet(exploration));
			WorldLandmarks landmarks = summary.landmarks();
			if (landmarks != null) {
				landmarks.tryMigrateXaeros(false);
				SurveyorClientEvents.Invoke.landmarksAdded(summary, landmarks.keySet(exploration));
			}
		}
	}

	public static void sendKnownData(ClientPlayNetworkHandler handler) {
		Table<RegistryKey<World>, RegionPos, BitSet> chunks = HashBasedTable.create();
		Map<RegistryKey<World>, Multimap<RegistryKey<Structure>, ChunkPos>> starts = new HashMap<>();
		Map<RegistryKey<World>, Multimap<UUID, Identifier>> landmarkKeys = new HashMap<>();
		boolean hasTerrain = false;
		boolean hasStructures = false;
		boolean hasLandmarks = false;
		for (WorldSummary summary : SurveyorClient.getSummaries(handler).values()) {
			WorldTerrain terrain = summary.terrain();
			if (terrain != null && Surveyor.CONFIG.networking.terrain.atLeast(NetworkMode.SOLO)) {
				chunks.row(summary.dimension()).putAll(terrain.bitSet(null));
				hasTerrain = true;
			}
			WorldStructures structures = summary.structures();
			if (structures != null && Surveyor.CONFIG.networking.structures.atLeast(NetworkMode.SOLO)) {
				starts.put(summary.dimension(), structures.keySet(null));
				hasStructures = true;
			}
			WorldLandmarks landmarks = summary.landmarks();
			if (landmarks != null && Surveyor.CONFIG.networking.landmarks.atLeast(NetworkMode.SOLO)) {
				landmarkKeys.put(summary.dimension(), landmarks.keySet(null));
				hasLandmarks = true;
			}
		}
		if (hasTerrain) new C2SKnownTerrainPacket(chunks).send();
		if (hasStructures) new C2SKnownStructuresPacket(starts).send();
		if (hasLandmarks) new C2SKnownLandmarksPacket(landmarkKeys).send();
	}

	@Override
	public void onInitializeClient() {
		SurveyorClientNetworking.init();
		ClientCommandRegistrationCallback.EVENT.register(SurveyorClientCommands::registerCommands);
		ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
			if (!MinecraftClient.getInstance().isInSingleplayer()) LOADING_CHUNKS.put(world.getRegistryKey(), chunk);
		});
		ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
			if (!MinecraftClient.getInstance().isInSingleplayer()) WorldTerrain.onChunkUnload(world, chunk);
		});
		ClientTickEvents.END_WORLD_TICK.register((world -> {
			if (MinecraftClient.getInstance().worldRenderer.getCompletedChunkCount() <= 10 || !MinecraftClient.getInstance().worldRenderer.isTerrainRenderComplete()) return;
			for (WorldChunk chunk : new HashSet<>(LOADING_CHUNKS.get(world.getRegistryKey()))) {
				WorldTerrain.onChunkLoad(world, chunk);
				ClientSummary.of(MinecraftClient.getInstance().getNetworkHandler()).personal.addChunk(world.getRegistryKey(), chunk.getPos(), false);
				LOADING_CHUNKS.remove(world.getRegistryKey(), chunk);
			}
		}));
		SurveyorEvents.Register.landmarksAdded(Surveyor.id("client"), ((summary, landmarks) -> {
			SurveyorExploration exploration = getExploration();
			if (exploration != null) SurveyorClientEvents.Invoke.landmarksAdded(summary, exploration.limit(summary.dimension(), summary.landmarks(), HashMultimap.create(landmarks)));
		}));
		SurveyorEvents.Register.landmarksRemoved(Surveyor.id("client"), SurveyorClientEvents.Invoke::landmarksRemoved);
		Surveyor.LOGGER.info("[Surveyor Client] is not a map mod either");
	}

	public record ClientExploration(Set<UUID> groupPlayers, Table<RegistryKey<World>, RegionPos, BitSet> chunks, Table<RegistryKey<World>, RegistryKey<Structure>, LongSet> starts) implements SurveyorExploration {
		@Override
		public Set<UUID> sharedPlayers() {
			Set<UUID> sharedPlayers = new HashSet<>();
			sharedPlayers.add(getClientUuid());
			sharedPlayers.addAll(groupPlayers);
			return sharedPlayers;
		}

		@Override
		public boolean personal() {
			return true;
		}

		@Override
		public void addStructure(RegistryKey<World> dimension, RegistryKey<Structure> structureKey, ChunkPos pos) {
			SurveyorExploration.super.addStructure(dimension, structureKey, pos);
			WorldSummary summary = tryGetSummary(dimension);
			if (summary != null) updateClientForAddStructure(summary, structureKey, pos);
		}

		@Override
		public void mergeRegion(RegistryKey<World> dimension, RegionPos regionPos, BitSet chunks, boolean updateClient) {
			SurveyorExploration.super.mergeRegion(dimension, regionPos, chunks, updateClient);
			WorldSummary summary = tryGetSummary(dimension);
			if (summary != null) updateClientForMergeRegion(summary, regionPos, chunks);
		}

		@Override
		public void addChunk(RegistryKey<World> dimension, ChunkPos pos, boolean updateClient) {
			SurveyorExploration.super.addChunk(dimension, pos, updateClient);
			WorldSummary summary = tryGetSummary(dimension);
			if (summary != null) updateClientForAddChunk(summary, pos);
		}
	}
}
