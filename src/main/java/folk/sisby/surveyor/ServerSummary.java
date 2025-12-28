package folk.sisby.surveyor;

import com.mojang.authlib.GameProfile;
import folk.sisby.surveyor.config.NetworkMode;
import folk.sisby.surveyor.packet.S2CGroupAmendedPacket;
import folk.sisby.surveyor.packet.S2CGroupChangedPacket;
import folk.sisby.surveyor.packet.S2CGroupUpdatedPacket;
import folk.sisby.surveyor.util.MapUtil;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class ServerSummary {
	public static final String KEY_GROUPS = "groups";
	public static final UUID HOST = UUID.fromString("00000000-0000-0000-0000-000000000000");
	private final MinecraftServer server;
	private final Map<UUID, PlayerSummary> offlineSummaries;
	private final Map<UUID, Set<UUID>> shareGroups;
	private final Map<RegistryKey<World>, WorldSummary> worlds;
	private boolean dirty = false;

	public ServerSummary(MinecraftServer server, Map<UUID, PlayerSummary> offlineSummaries, @Nullable Map<UUID, Set<UUID>> shareGroups) {
		this.server = server;
		this.offlineSummaries = offlineSummaries;
		this.shareGroups = shareGroups;
		this.worlds = new HashMap<>();
	}

	public static ServerSummary of(MinecraftServer server) {
		return ((SurveyorServer) server).surveyor$getSummary();
	}

	public static Map<UUID, Set<UUID>> loadShareGroups(MinecraftServer server) {
		File folder = Surveyor.getSavePath(World.OVERWORLD, server);
		NbtCompound sharingNbt = new NbtCompound();
		File sharingFile = new File(folder, "sharing.dat");
		if (sharingFile.exists()) {
			try {
				sharingNbt = NbtIo.readCompressed(sharingFile);
			} catch (IOException | CrashException e) {
				Surveyor.LOGGER.error("[Surveyor] Error loading sharing file.", e);
			}
		}
		Map<UUID, Set<UUID>> shareGroups = new ConcurrentHashMap<>();
		sharingNbt.getList(KEY_GROUPS, NbtElement.LIST_TYPE).stream().map(l -> ((NbtList) l).stream().map(s -> UUID.fromString(s.asString())).collect(Collectors.toCollection(HashSet::new))).forEach(set -> {
			for (UUID uuid : set) {
				shareGroups.put(uuid, set);
			}
		});
		return shareGroups;
	}

	public static ServerSummary load(MinecraftServer server) {
		Map<UUID, Set<UUID>> shareGroups = Surveyor.CONFIG.networking.globalSharing ? null : loadShareGroups(server);

		File playerFolder = server.getSavePath(WorldSavePath.PLAYERDATA).toFile();

		Map<UUID, PlayerSummary> offlineSummaries = new ConcurrentHashMap<>();

		NbtCompound hostData = server.getSaveProperties().getPlayerData();
		UUID hostProfile = Optional.ofNullable(server.getHostProfile()).map(GameProfile::getId).orElse(null);
		if (hostData != null) {
			if (hostProfile != null) hostData.putString(PlayerSummary.KEY_USERNAME, server.getHostProfile().getName());
			offlineSummaries.put(ServerSummary.HOST, new PlayerSummary.OfflinePlayerSummary(ServerSummary.HOST, hostData, false));
		}

		for (File file : Optional.ofNullable(playerFolder.listFiles((dir, name) -> name.endsWith(".dat"))).orElse(new File[0])) {
			UUID uuid;
			try {
				uuid = UUID.fromString(file.getName().substring(0, file.getName().length() - ".dat".length()));
				if (uuid.equals(hostProfile)) continue;
			} catch (IllegalArgumentException ex) {
				continue;
			}
			if (shareGroups != null && !shareGroups.containsKey(uuid)) continue;
			try {
				NbtCompound playerNbt = NbtIo.readCompressed(file);
				offlineSummaries.put(uuid, new PlayerSummary.OfflinePlayerSummary(uuid, playerNbt, false));
			} catch (IOException | CrashException e) {
				Surveyor.LOGGER.error("[Surveyor] Error loading offline player data for {}!", uuid, e);
			}
		}

		if (shareGroups != null) {
			for (UUID uuid : shareGroups.keySet()) {
				if (!offlineSummaries.containsKey(uuid)) {
					Surveyor.LOGGER.warn("[Surveyor] Player data was missing for shared player {}! Removing from groups...", uuid);
					shareGroups.get(uuid).remove(uuid);
					shareGroups.remove(uuid);
				}
			}
		}

		return new ServerSummary(server, offlineSummaries, shareGroups);
	}

	public static void onPlayerJoin(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
		ServerPlayerEntity player = handler.getPlayer();
		ServerSummary serverSummary = ServerSummary.of(server);
		UUID uuid = Surveyor.getUuid(player);
		boolean known = serverSummary.offlineSummaries.containsKey(uuid);
		if (!known) serverSummary.createPlayer(player);
		if (serverSummary.getGroup(uuid).size() > 1) {
			// initial exploration
			Map<UUID, PlayerSummary> groupSummaries = serverSummary.getGroupSummaries(uuid);
			new S2CGroupChangedPacket(groupSummaries, serverSummary.getSharingExploration(uuid, Surveyor.CONFIG.networking.terrain, false).chunks(), serverSummary.getSharingExploration(uuid, Surveyor.CONFIG.networking.structures, false).starts()).send(player);
			// initial offline group positions
			new S2CGroupUpdatedPacket(groupSummaries).send(player);
		}
		// update global group members
		if (!known && Surveyor.CONFIG.networking.globalSharing) new S2CGroupAmendedPacket(uuid).send(uuid, server, server.getPlayerManager().getPlayerList(), NetworkMode.GROUP, false);
	}

	public static void onTick(MinecraftServer server) {
		if ((server.getTicks() % Surveyor.CONFIG.networking.positionTicks) != 0) return;
		ServerSummary serverSummary = ServerSummary.of(server);
		for (Set<UUID> group : serverSummary.getPositionGroups()) {
			Map<UUID, PlayerSummary> onlinePlayers = new HashMap<>();
			for (UUID uuid : group) {
				var player = server.getPlayerManager().getPlayer(uuid);
				if (player != null) onlinePlayers.put(uuid, PlayerSummary.of(player));
			}
			if (onlinePlayers.size() > 1) new S2CGroupUpdatedPacket(onlinePlayers).send(null, server, onlinePlayers.keySet().stream().map(server.getPlayerManager()::getPlayer).toList(), Surveyor.CONFIG.networking.positions, true);
		}
	}

	public WorldSummary getWorld(RegistryKey<World> dimension) {
		return worlds.computeIfAbsent(dimension, dim -> new WorldSummary(server, dim, server.getRegistryManager(), Surveyor.getSavePath(dim, server)));
	}

	public void loadWorlds() {
		for (RegistryKey<World> dimension : server.getWorldRegistryKeys()) {
			WorldSummary summary = getWorld(dimension);
			if (summary.terrain() != null) SurveyorEvents.Invoke.terrainUpdated(summary, summary.terrain().bitSet(null));
			if (summary.structures() != null) SurveyorEvents.Invoke.structuresAdded(summary, summary.structures().keySet(null));
			if (summary.landmarks() != null) SurveyorEvents.Invoke.landmarksAdded(summary, summary.landmarks().keySet(null));
		}
	}

	public void save(boolean force, boolean suppressLogs) {
		if (!isDirty() && StreamSupport.stream(server.getWorlds().spliterator(), false).map(WorldSummary::of).noneMatch(WorldSummary::isDirty)) return;
		if (!suppressLogs) Surveyor.LOGGER.info("[Surveyor] Saving server data...");
		for (ServerWorld world : server.getWorlds()) {
			if (!world.savingDisabled || force) {
				WorldSummary.of(world).save(world, Surveyor.getSavePath(world.getRegistryKey(), server), suppressLogs);
			}
		}
		File folder = Surveyor.getSavePath(World.OVERWORLD, server);
		if (isDirty()) {
			File sharingFile = new File(folder, "sharing.dat");
			try {
				NbtIo.writeCompressed(writeNbt(new NbtCompound()), sharingFile);
				dirty = false;
			} catch (IOException e) {
				Surveyor.LOGGER.error("[Surveyor] Error writing sharing file.", e);
			}
		}
		if (!suppressLogs) Surveyor.LOGGER.info("[Surveyor] Finished saving server data.");
	}

	private NbtCompound writeNbt(NbtCompound nbt) {
		if (shareGroups != null) nbt.put(KEY_GROUPS, new NbtList(shareGroups.values().stream().filter(s -> s.size() > 1).map(s -> (NbtElement) new NbtList(s.stream().map(u -> (NbtElement) NbtString.of(u.toString())).toList(), NbtElement.STRING_TYPE)).toList(), NbtElement.LIST_TYPE));
		return nbt;
	}

	public PlayerSummary getPlayer(UUID uuid) {
		ServerPlayerEntity player = Surveyor.getPlayer(server, uuid);
		if (player != null) {
			return PlayerSummary.of(player);
		} else {
			return offlineSummaries.get(uuid);
		}
	}

	public SurveyorExploration getExploration(UUID player) {
		PlayerSummary summary = getPlayer(player);
		return summary == null ? null : summary.exploration();
	}

	public void createPlayer(ServerPlayerEntity player) {
		offlineSummaries.put(Surveyor.getUuid(player), new PlayerSummary.OfflinePlayerSummary(player));
	}

	public void updatePlayer(UUID uuid, NbtCompound nbt, boolean online) {
		PlayerSummary newSummary = new PlayerSummary.OfflinePlayerSummary(uuid, nbt, online);
		offlineSummaries.put(uuid, newSummary);
		S2CGroupUpdatedPacket.of(uuid, newSummary).send(null, server, getSharingPlayers(uuid, Surveyor.CONFIG.networking.positions, false), Surveyor.CONFIG.networking.positions, false);
	}

	public Set<Set<UUID>> getPositionGroups() {
		return Surveyor.CONFIG.networking.positions.atLeast(NetworkMode.SERVER) ? Set.of(offlineSummaries.keySet()) : Surveyor.CONFIG.networking.positions.atMost(NetworkMode.SOLO) ? Set.of() : getGroups();
	}

	public Set<Set<UUID>> getGroups() {
		return shareGroups == null ? new HashSet<>(Set.of(new HashSet<>(offlineSummaries.keySet()))) : new HashSet<>(shareGroups.values());
	}

	public Set<UUID> getGroup(UUID player) {
		return shareGroups == null ? new HashSet<>(offlineSummaries.keySet()) : shareGroups.computeIfAbsent(player, p -> new HashSet<>(Set.of(p)));
	}

	public Map<UUID, PlayerSummary> getAllSummaries() {
		Map<UUID, PlayerSummary> map = new HashMap<>();
		for (UUID u : offlineSummaries.keySet()) {
			if (getPlayer(u) != null) map.put(u, getPlayer(u));
		}
		return map;
	}

	public Map<UUID, PlayerSummary> getGroupSummaries(UUID player) {
		Map<UUID, PlayerSummary> map = new HashMap<>();
		for (UUID u : getGroup(player)) {
			if (getPlayer(u) != null) map.put(u, getPlayer(u));
		}
		return map;
	}

	public void joinGroup(UUID player1, UUID player2) {
		if (shareGroups == null) return;
		if (getGroup(player1).size() > 1 && getGroup(player2).size() > 1) throw new IllegalStateException("Can't merge two groups!");
		if (getGroup(player1).size() > 1) {
			getGroup(player1).add(player2);
			shareGroups.put(player2, getGroup(player1));
		} else {
			getGroup(player2).add(player1);
			shareGroups.put(player1, getGroup(player2));
		}
		for (ServerPlayerEntity friend : getSharingPlayers(player1, NetworkMode.GROUP, true)) {
			UUID uuid = Surveyor.getUuid(friend);
			new S2CGroupChangedPacket(getGroupSummaries(uuid), getSharingExploration(uuid, Surveyor.CONFIG.networking.terrain, false).chunks(), getSharingExploration(uuid, Surveyor.CONFIG.networking.structures, false).starts()).send(friend);
		}
		dirty();
	}

	public void leaveGroup(UUID player) {
		if (shareGroups == null) return;
		Set<ServerPlayerEntity> groupPlayers = getSharingPlayers(player, NetworkMode.GROUP, true);
		getGroup(player).remove(player); // Shares set instance with group members.
		shareGroups.put(player, new HashSet<>());
		getGroup(player).add(player);
		for (ServerPlayerEntity friend : groupPlayers) {
			UUID uuid = Surveyor.getUuid(friend);
			new S2CGroupChangedPacket(getGroupSummaries(uuid), getSharingExploration(uuid, Surveyor.CONFIG.networking.terrain, false).chunks(), getSharingExploration(uuid, Surveyor.CONFIG.networking.structures, false).starts()).send(friend);
		}
		dirty();
	}

	public int groupSize(UUID player) {
		return getGroup(player).size();
	}

	public Set<PlayerSummary> groupPlayers(UUID player) {
		return getGroup(player).stream().map(this::getPlayer).collect(Collectors.toSet());
	}

	public Set<UUID> getSharing(UUID player, NetworkMode mode, boolean withSelf) {
		return mode.atMost(NetworkMode.SOLO) && !withSelf ? Set.of() : mode.atMost(NetworkMode.SOLO) ? Set.of(player) : mode.atMost(NetworkMode.GROUP) ? getGroup(player) : offlineSummaries.keySet();
	}

	public SurveyorExploration getSharingExploration(UUID player, NetworkMode mode, boolean withSelf) {
		Set<UUID> sharing = getSharing(player, mode, withSelf);
		if (mode.atLeast(NetworkMode.SERVER)) return new PlayerSummary.OfflinePlayerSummary.OfflinePlayerExploration(sharing,
			MapUtil.asTable(worlds.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> Optional.ofNullable(e.getValue().terrain()).map(t -> t.bitSet(null)).orElse(Map.of())))),
			MapUtil.asTable(worlds.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> Optional.ofNullable(e.getValue().structures()).map(t -> MapUtil.asListMap(t.keySet(null)).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e2 -> LongSet.of(e2.getValue().stream().mapToLong(ChunkPos::toLong).toArray())))).orElse(Map.of())))),
			false
		);
		return PlayerSummary.OfflinePlayerSummary.OfflinePlayerExploration.ofMerged(sharing.stream().map(this::getExploration).collect(Collectors.toSet()));
	}

	public Set<ServerPlayerEntity> getSharingPlayers(UUID player, NetworkMode mode, boolean withSelf) {
		Set<UUID> sharing = getSharing(player, mode, withSelf);
		return server.getPlayerManager().getPlayerList().stream().filter(p -> sharing.contains(Surveyor.getUuid(p))).collect(Collectors.toSet());
	}

	public boolean isDirty() {
		return dirty && shareGroups != null;
	}

	private void dirty() {
		dirty = true;
	}
}
