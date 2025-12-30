package folk.sisby.surveyor.client;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Sets;
import folk.sisby.surveyor.PlayerSummary;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.WorldSummary;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.crash.CrashException;
import net.minecraft.world.World;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClientSummary {
	public static final String KEY_SHARED = "shared";
	private final long biomeSeed;
	private final ClientPlayNetworkHandler handler;
	private final Map<UUID, PlayerSummary> players;
	private final Map<RegistryKey<World>, WorldSummary> worlds;
	public final SurveyorClient.ClientExploration personal;
	public final SurveyorClient.ClientExploration shared;
	public final File saveFile;

	public ClientSummary(long biomeSeed, ClientPlayNetworkHandler handler) {
		this.biomeSeed = biomeSeed;
		this.handler = handler;
		this.players = new HashMap<>();
		this.worlds = new ConcurrentHashMap<>();
		this.personal = new SurveyorClient.ClientExploration(new HashSet<>(), HashBasedTable.create(), HashBasedTable.create());
		this.shared = new SurveyorClient.ClientExploration(new HashSet<>(), HashBasedTable.create(), HashBasedTable.create());
		this.saveFile = SurveyorClient.getSavePath(biomeSeed).toPath().resolve(SurveyorClient.getClientUuid().toString() + ".dat").toFile();
	}

	public static ClientSummary of(ClientPlayNetworkHandler handler) {
		return ((SurveyorNetworkHandler) handler).surveyor$getSummary();
	}

	public void mergeSummaries(Map<UUID, PlayerSummary> summaries) {
		players.putAll(summaries);
	}

	public void matchSummaries(Map<UUID, PlayerSummary> summaries) {
		players.clear();
		players.putAll(summaries);
	}

	public Map<UUID, PlayerSummary> players(Set<UUID> group) {
		Map<UUID, PlayerSummary> outMap = new HashMap<>();
		for (UUID uuid : Sets.union(group, players.keySet())) {
			PlayerEntity player = handler.getWorld().getPlayerByUuid(uuid);
			if (player != null) {
				outMap.put(uuid, new PlayerSummary.PlayerEntitySummary(player));
			} else {
				outMap.put(uuid, players.get(uuid));
			}
		}
		return outMap;
	}

	public WorldSummary getWorld(RegistryKey<World> dimension) {
		return worlds.computeIfAbsent(dimension, k -> new WorldSummary(null, dimension, handler.getRegistryManager(), SurveyorClient.getWorldSavePath(dimension, biomeSeed)));
	}

	public void connect() {
		NbtCompound explorationNbt = new NbtCompound();
		if (saveFile.exists()) {
			try {
				explorationNbt = NbtIo.readCompressed(saveFile.toPath(), NbtSizeTracker.ofUnlimitedBytes());
			} catch (IOException | CrashException e) {
				Surveyor.LOGGER.error("[Surveyor] Error loading client exploration file.", e);
			}
		}
		this.personal.read(explorationNbt);
		this.shared.read(explorationNbt.getCompound(KEY_SHARED).orElse(new NbtCompound()));
		SurveyorClient.getSummaries(handler); // load it up!
		SurveyorClient.handleInitialLoad(handler); // event it up!
		SurveyorClient.sendKnownData(handler); // network it up!
	}

	public void disconnect() {
		SurveyorClient.clearLoadingChunks();
		for (WorldSummary summary : worlds.values()) {
			summary.save(null, SurveyorClient.getWorldSavePath(summary.dimension(), biomeSeed), false);
		}
		try {
			NbtCompound nbt = personal.write(new NbtCompound());
			NbtCompound sharedNbt = shared.write(new NbtCompound());
			nbt.put(KEY_SHARED, sharedNbt);
			NbtIo.writeCompressed(nbt, saveFile.toPath());
		} catch (IOException e) {
			Surveyor.LOGGER.error("[Surveyor] Error saving client exploration file.", e);
		}
	}

	public void leaveWorld(RegistryKey<World> dimension) {
		if (worlds.containsKey(dimension)) worlds.get(dimension).save(null, SurveyorClient.getWorldSavePath(dimension, biomeSeed), false);
	}
}
