package folk.sisby.surveyor.client;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import folk.sisby.surveyor.PlayerSummary;
import folk.sisby.surveyor.ServerSummary;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.SurveyorEvents;
import folk.sisby.surveyor.SurveyorExploration;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.config.NetworkMode;
import folk.sisby.surveyor.packet.C2SKnownLandmarksPacket;
import folk.sisby.surveyor.packet.C2SKnownStructuresPacket;
import folk.sisby.surveyor.packet.C2SKnownTerrainPacket;
import folk.sisby.surveyor.terrain.WorldTerrainSummary;
import folk.sisby.surveyor.util.RegionPos;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.nbt.NbtCrashException;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.structure.Structure;
import org.apache.commons.io.FileUtils;

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

	public static File getSavePath(World world) {
		String saveFolder = String.valueOf(world.getBiomeAccess().seed);
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

	public static File getWorldSavePath(World world) {
		String dimNamespace = world.getRegistryKey().getValue().getNamespace();
		String dimPath = world.getRegistryKey().getValue().getPath();
		return getSavePath(world).toPath().resolve(dimNamespace).resolve(dimPath).toFile();
	}

	public static boolean serverSupported() {
		return ClientPlayNetworking.canSend(C2SKnownTerrainPacket.ID);
	}

	public static Map<UUID, PlayerSummary> getFriends() {
		if (MinecraftClient.getInstance().isIntegratedServerRunning()) {
			MinecraftServer server = MinecraftClient.getInstance().getServer();
			return ServerSummary.of(server).getGroupSummaries(getClientUuid(), server);
		} else {
			ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
			if (handler == null) return new HashMap<>();
			NetworkHandlerSummary handlerSummary = NetworkHandlerSummary.of(handler);
			return ClientExploration.SHARED.sharedPlayers().stream().collect(Collectors.toMap(u -> u, handlerSummary::getPlayer));
		}
	}

	public static SurveyorExploration getExploration() {
		if (MinecraftClient.getInstance().isIntegratedServerRunning()) {
			return SurveyorExploration.ofShared(getClientUuid(), MinecraftClient.getInstance().getServer());
		} else {
			Set<SurveyorExploration> set = new HashSet<>();
			set.add(ClientExploration.INSTANCE);
			set.add(ClientExploration.SHARED);
			return PlayerSummary.OfflinePlayerSummary.OfflinePlayerExploration.ofMerged(set);
		}
	}

	public static SurveyorExploration getPersonalExploration() {
		if (MinecraftClient.getInstance().isIntegratedServerRunning()) {
			return SurveyorExploration.of(getClientUuid(), MinecraftClient.getInstance().getServer());
		} else {
			return ClientExploration.INSTANCE;
		}
	}

	public static ClientExploration getSharedExploration() {
		if (MinecraftClient.getInstance().isIntegratedServerRunning()) {
			throw new IllegalStateException("You can't edit shared exploration in singleplayer!");
		} else {
			return ClientExploration.SHARED;
		}
	}

	public static UUID getClientUuid() { // UUID needs to always match what the server is using.
		if (MinecraftClient.getInstance().isIntegratedServerRunning()) return ServerSummary.HOST;
		GameProfile profile = ((SurveyorNetworkHandler) MinecraftClient.getInstance().getNetworkHandler()).getProfile();
		return profile.getId();
	}

	public static ServerWorld stealServerWorld(RegistryKey<World> worldKey) {
		MinecraftServer integratedServer = MinecraftClient.getInstance().getServer();
		if (integratedServer == null) return null;
		return integratedServer.getWorld(worldKey);
	}

	@Override
	public void onInitializeClient() {
		SurveyorClientNetworking.init();
		ClientCommandRegistrationCallback.EVENT.register(SurveyorClientCommands::registerCommands);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(ClientExploration::onLoad));
		ClientPlayConnectionEvents.DISCONNECT.register(((handler, client) -> {
			LOADING_CHUNKS.clear();
			ClientExploration.onUnload();
		}));
		ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
			if (WorldSummary.of(world).isClient()) {
				LOADING_CHUNKS.put(world.getRegistryKey(), chunk);
			}
		});
		ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
			if (WorldSummary.of(world).isClient()) WorldTerrainSummary.onChunkUnload(world, chunk);
		});
		ClientTickEvents.END_WORLD_TICK.register((world -> {
			if (MinecraftClient.getInstance().worldRenderer.getCompletedChunkCount() <= 10 || !MinecraftClient.getInstance().worldRenderer.isTerrainRenderComplete()) return;
			for (WorldChunk chunk : new HashSet<>(LOADING_CHUNKS.get(world.getRegistryKey()))) {
				WorldTerrainSummary.onChunkLoad(world, chunk);
				ClientExploration.INSTANCE.addChunk(world.getRegistryKey(), chunk.getPos());
				LOADING_CHUNKS.remove(world.getRegistryKey(), chunk);
			}
		}));
		ClientTickEvents.END_WORLD_TICK.register((world -> {
			if (!SurveyorClientEvents.INITIALIZING_WORLD) return;
			if (MinecraftClient.getInstance().player != null && getExploration() != null) {
				SurveyorClientEvents.INITIALIZING_WORLD = false;
				if (WorldSummary.of(world).isClient()) {
					WorldSummary summary = WorldSummary.of(world);
					if (summary.terrain() != null && Surveyor.CONFIG.networking.terrain.atLeast(NetworkMode.SOLO)) new C2SKnownTerrainPacket(summary.terrain().bitSet(null)).send(world.getRegistryManager());
					if (summary.structures() != null && Surveyor.CONFIG.networking.structures.atLeast(NetworkMode.SOLO)) new C2SKnownStructuresPacket(summary.structures().keySet(null)).send(world.getRegistryManager());
					if (summary.landmarks() != null && Surveyor.CONFIG.networking.landmarks.atLeast(NetworkMode.SOLO)) new C2SKnownLandmarksPacket(summary.landmarks().keySet(null)).send(world.getRegistryManager());
				}
				SurveyorClientEvents.Invoke.worldLoad(MinecraftClient.getInstance().player.clientWorld, MinecraftClient.getInstance().player);
			}
		}));
		SurveyorEvents.Register.landmarksAdded(Surveyor.id("client"), ((world, worldLandmarks, landmarks) -> {
			SurveyorExploration exploration = getExploration();
			if (exploration != null) SurveyorClientEvents.Invoke.landmarksAdded(world, exploration.limitLandmarkKeySet(world.getRegistryKey(), worldLandmarks, HashMultimap.create(landmarks)));
		}));
		SurveyorEvents.Register.landmarksRemoved(Surveyor.id("client"), (world, summary, landmarks) -> SurveyorClientEvents.Invoke.landmarksRemoved(world, landmarks));
		Surveyor.LOGGER.info("[Surveyor Client] is not a map mod either");
	}

	public record ClientExploration(Set<UUID> groupPlayers, Map<RegistryKey<World>, Map<RegionPos, BitSet>> terrain, Map<RegistryKey<World>, Map<RegistryKey<Structure>, LongSet>> structures) implements SurveyorExploration {
		public static final String KEY_SHARED = "shared";
		public static final ClientExploration INSTANCE = new ClientExploration(new HashSet<>(), new HashMap<>(), new HashMap<>());
		public static final ClientExploration SHARED = new ClientExploration(new HashSet<>(), new HashMap<>(), new HashMap<>());
		public static File saveFile = null;

		public static void onLoad() {
			if (WorldSummary.of(MinecraftClient.getInstance().world).isClient()) {
				saveFile = getSavePath(MinecraftClient.getInstance().world).toPath().resolve(getClientUuid().toString() + ".dat").toFile();
				NbtCompound explorationNbt = new NbtCompound();
				if (saveFile.exists()) {
					try {
						explorationNbt = NbtIo.readCompressed(saveFile.toPath(), NbtSizeTracker.ofUnlimitedBytes());
					} catch (IOException | NbtCrashException e) {
						Surveyor.LOGGER.error("[Surveyor] Error loading client exploration file.", e);
					}
				}
				ClientExploration.INSTANCE.read(explorationNbt);
				ClientExploration.SHARED.read(explorationNbt.getCompound(KEY_SHARED).orElse(new NbtCompound()));
			}
		}

		public static void onUnload() {
			if (saveFile != null) {
				try {
					NbtCompound nbt = ClientExploration.INSTANCE.write(new NbtCompound());
					NbtCompound sharedNbt = ClientExploration.SHARED.write(new NbtCompound());
					nbt.put(KEY_SHARED, sharedNbt);
					NbtIo.writeCompressed(nbt, saveFile.toPath());
				} catch (IOException e) {
					Surveyor.LOGGER.error("[Surveyor] Error saving client exploration file.", e);
				}
				saveFile = null;
			}
			ClientExploration.INSTANCE.terrain.clear();
			ClientExploration.INSTANCE.structures.clear();
			ClientExploration.INSTANCE.groupPlayers.clear();
			ClientExploration.SHARED.terrain.clear();
			ClientExploration.SHARED.structures.clear();
			ClientExploration.SHARED.groupPlayers.clear();
		}

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
		public void addStructure(RegistryKey<World> worldKey, RegistryKey<Structure> structureKey, ChunkPos pos) {
			SurveyorExploration.super.addStructure(worldKey, structureKey, pos);
			updateClientForAddStructure(MinecraftClient.getInstance().world, structureKey, pos);
		}

		@Override
		public void mergeRegion(RegistryKey<World> worldKey, RegionPos regionPos, BitSet bitSet) {
			SurveyorExploration.super.mergeRegion(worldKey, regionPos, bitSet);
			updateClientForMergeRegion(MinecraftClient.getInstance().world, regionPos, bitSet);
		}

		@Override
		public void addChunk(RegistryKey<World> worldKey, ChunkPos pos) {
			SurveyorExploration.super.addChunk(worldKey, pos);
			updateClientForAddChunk(MinecraftClient.getInstance().world, pos);
		}
	}
}
