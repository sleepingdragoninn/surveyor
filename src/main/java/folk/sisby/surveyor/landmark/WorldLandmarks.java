package folk.sisby.surveyor.landmark;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.SurveyorEvents;
import folk.sisby.surveyor.SurveyorExploration;
import folk.sisby.surveyor.client.SurveyorClient;
import folk.sisby.surveyor.config.NetworkMode;
import folk.sisby.surveyor.config.SystemMode;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.packet.C2SKnownLandmarksPacket;
import folk.sisby.surveyor.packet.SyncLandmarksAddedPacket;
import folk.sisby.surveyor.packet.SyncLandmarksRemovedPacket;
import folk.sisby.surveyor.util.DispatchMapCodec;
import folk.sisby.surveyor.util.MapUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.Uuids;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public class WorldLandmarks {
	public static final UUID GLOBAL = UUID.fromString("99999999-9999-9999-9999-999999999999");
	public static final String KEY_LANDMARKS = "landmarks";
	public static final String KEY_REMOVED = "removed";
	public static final Codec<Map<UUID, Map<Identifier, Landmark>>> CODEC = DispatchMapCodec.of(
		Uuids.STRING_CODEC,
		uuid -> DispatchMapCodec.of(
			Identifier.CODEC,
			id -> Landmark.createCodec(uuid, id)
		)
	);
	public static final Codec<Multimap<UUID, Identifier>> REMOVED_CODEC = Codec.unboundedMap(
		Uuids.STRING_CODEC,
		Codec.list(Identifier.CODEC)
	).xmap(MapUtil::asMultiMap, MapUtil::asListMap);
	public static final int[] XAERO_COLORS = new int[]{
		0xFF_000000, 0xFF_0000AA, 0xFF_00AA00, 0xFF_00AAAA, 0xFF_AA0000, 0xFF_AA00AA, 0xFF_ffAA00, 0xFF_AAAAAA, 0xFF_555555, 0xFF_5555FF, 0xFF_55FF55, 0xFF_55FFFF, 0xFF_FF0000, 0xFF_FF55FF, 0xFF_FFFF55, 0xFF_FFFFFF
	};

	protected final RegistryKey<World> worldKey;
	protected final Map<UUID, Map<Identifier, Landmark>> landmarks = new ConcurrentHashMap<>();
	protected final @Nullable Multimap<UUID, Identifier> removed;
	protected boolean dirty;

	public WorldLandmarks(RegistryKey<World> worldKey, Map<UUID, Map<Identifier, Landmark>> landmarks, Multimap<UUID, Identifier> removed, boolean dirty) {
		this.worldKey = worldKey;
		this.landmarks.putAll(landmarks);
		this.removed = removed == null ? null : Multimaps.synchronizedSetMultimap(HashMultimap.create(removed));
		if (this.removed != null) this.landmarks.forEach((uuid, map) -> map.keySet().forEach(id -> removed.remove(uuid, id)));
		this.dirty = dirty;
	}

	public static WorldLandmarks load(World world, File folder, boolean isClient) {
		NbtCompound landmarkNbt = new NbtCompound();
		File landmarksFile = new File(folder, "landmarks.dat");
		if (landmarksFile.exists()) {
			try {
				landmarkNbt = NbtIo.readCompressed(landmarksFile);
			} catch (IOException | CrashException e) {
				Surveyor.LOGGER.error("[Surveyor] Error loading landmarks file for {}.", world.getRegistryKey().getValue(), e);
			}
		}
		WorldLandmarks worldLandmarks = fromNbt(world, landmarkNbt, landmarksFile, isClient);
		if (!isClient) worldLandmarks.tryMigrateXaeros(world, false); // for singleplayer
		return worldLandmarks;
	}

	public NbtCompound writeNbt(NbtCompound nbt) {
		nbt.put(KEY_LANDMARKS, CODEC.encodeStart(NbtOps.INSTANCE, landmarks).resultOrPartial(Surveyor.LOGGER::error).orElseThrow());
		if (removed != null) nbt.put(KEY_REMOVED, REMOVED_CODEC.encodeStart(NbtOps.INSTANCE, removed).resultOrPartial(Surveyor.LOGGER::error).orElseThrow());
		return nbt;
	}

	public static WorldLandmarks fromNbt(World world, NbtCompound nbt, File landmarksFile, boolean isClient) {
		NbtCompound landmarks = nbt.getCompound(KEY_LANDMARKS);
		boolean dirty = false;
		Map<UUID, Map<Identifier, Landmark>> outMap = new HashMap<>();
		Multimap<UUID, Identifier> removedMap = null;
		if (landmarks.getKeys().stream().anyMatch(k -> k.contains(":"))) { // 0.X
			Surveyor.LOGGER.warn("[Surveyor] Partially recovering landmarks from 0.X");
			try {
				Files.copy(landmarksFile.toPath(), landmarksFile.toPath().resolveSibling("landmarks.dat_v0"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				throw new RuntimeException("Surveyor failed to back up v0 landmarks, was the file locked?", e);
			}
			try {
				for (String key : landmarks.getKeys()) {
					NbtCompound type = landmarks.getCompound(key);
					Identifier typeId = Identifier.tryParse(key);
					for (String coords : type.getKeys()) {
						NbtCompound landmark = type.getCompound(coords);
						UUID owner = landmark.contains("owner") ? Uuids.CODEC.decode(NbtOps.INSTANCE, landmark.get("owner")).resultOrPartial(Surveyor.LOGGER::error).orElseThrow().getFirst() : GLOBAL;
						BlockPos pos = new BlockPos(Integer.parseInt(coords.split(",")[0]), Integer.parseInt(coords.split(",")[1]), Integer.parseInt(coords.split(",")[2]));
						DyeColor dye = !landmark.contains("color") ? null : DyeColor.CODEC.decode(NbtOps.INSTANCE, landmark.get("color")).resultOrPartial(Surveyor.LOGGER::error).map(Pair::getFirst).orElse(null);
						Identifier id = (landmark.contains("texture") ? Identifier.tryParse(landmark.getString("texture")) : typeId).withSuffixedPath((dye == null ? "" : "/" + dye.getName()) + "/" + pos.getX() + (pos.getY() == 0 ? "" : "/" + pos.getY()) + "/" + pos.getZ());
						outMap.computeIfAbsent(owner, u -> new HashMap<>()).put(id, Landmark.create(owner, id, b -> b
							.add(LandmarkComponentTypes.POS, pos)
							.add(LandmarkComponentTypes.BOX, !landmark.contains("box") ? null : BlockBox.CODEC.decode(NbtOps.INSTANCE, landmark.get("box")).resultOrPartial(Surveyor.LOGGER::error).map(Pair::getFirst).orElse(null))
							.add(LandmarkComponentTypes.COLOR, dye == null ? null : dye.getFireworkColor())
							.add(LandmarkComponentTypes.NAME, !landmark.contains("name") ? null : Codecs.TEXT.decode(NbtOps.INSTANCE, landmark.get("name")).resultOrPartial(Surveyor.LOGGER::error).map(Pair::getFirst).orElse(null))
							.add(LandmarkComponentTypes.SEED, !landmark.contains("seed") ? null : landmark.getInt("seed"))
							.add(LandmarkComponentTypes.TIME, !landmark.contains("created") ? null : landmark.getLong("created"))
						));
						dirty = true;
					}
				}
				Surveyor.LOGGER.info("[Surveyor] Recovered {} landmarks from legacy data.", outMap.values().stream().mapToInt(Map::size).sum());
			} catch (Exception e) {
				Surveyor.LOGGER.error("[Surveyor] Encountered an error during v0 landmark migration, skipping...", e);
			}
		} else {
			if (!landmarks.isEmpty()) CODEC.decode(NbtOps.INSTANCE, landmarks).resultOrPartial(Surveyor.LOGGER::error).orElseThrow().getFirst().forEach((uuid, map) -> map.forEach((id, landmark) -> outMap.computeIfAbsent(uuid, u -> new HashMap<>()).put(id, landmark)));
			if (!isClient) {
				NbtCompound removed = nbt.getCompound(KEY_REMOVED);
				if (!removed.isEmpty()) removedMap = REMOVED_CODEC.decode(NbtOps.INSTANCE, removed).resultOrPartial(Surveyor.LOGGER::error).orElseThrow().getFirst();
			}
		}
		return new WorldLandmarks(world.getRegistryKey(), outMap, removedMap, dirty);
	}

	public boolean contains(UUID uuid, Identifier id) {
		return landmarks.containsKey(uuid) && landmarks.get(uuid).containsKey(id);
	}

	public @Nullable Landmark get(UUID uuid, Identifier id) {
		return contains(uuid, id) ? landmarks.get(uuid).get(id) : null;
	}

	@SuppressWarnings("unchecked")
	public <T extends Landmark> Map<Identifier, T> asMap(UUID uuid, SurveyorExploration exploration) {
		Map<Identifier, T> outMap = new HashMap<>();
		if (landmarks.containsKey(uuid)) landmarks.get(uuid).forEach((id, landmark) -> {
			if (exploration == null || exploration.exploredLandmark(worldKey, landmark)) outMap.put(id, (T) landmark);
		});
		return outMap;
	}

	public Map<UUID, Map<Identifier, Landmark>> asMap(SurveyorExploration exploration) {
		Map<UUID, Map<Identifier, Landmark>> outmap = new HashMap<>();
		landmarks.forEach((uuid, map) -> map.forEach((id, landmark) -> {
			if (exploration == null || exploration.exploredLandmark(worldKey, landmark)) outmap.computeIfAbsent(uuid, t -> new HashMap<>()).put(id, landmark);
		}));
		return outmap;
	}

	public Multimap<UUID, Identifier> keySet(SurveyorExploration exploration) {
		Multimap<UUID, Identifier> outMap = HashMultimap.create();
		landmarks.forEach((uuid, map) -> map.forEach((id, landmark) -> {
			if (exploration == null || exploration.exploredLandmark(worldKey, landmark)) outMap.put(uuid, id);
		}));
		return outMap;
	}

	public Multimap<UUID, Identifier> removed() {
		Multimap<UUID, Identifier> outMap = HashMultimap.create();
		if (removed != null) outMap.putAll(removed);
		return outMap;
	}

	public void handleChanged(World world, Map<UUID, Map<Identifier, Landmark>> changed, boolean local, @Nullable ServerPlayerEntity sender) {
		if (changed.isEmpty()) return;
		Map<UUID, Map<Identifier, Landmark>> landmarksAddedChanged = new HashMap<>();
		Map<UUID, Map<Identifier, Landmark>> landmarksRemoved = new HashMap<>();
		changed.forEach((uuid, map) -> map.forEach((id, landmark) -> {
			if (contains(uuid, id)) {
				landmarksAddedChanged.computeIfAbsent(uuid, t -> new HashMap<>()).put(id, landmark);
			} else {
				landmarksRemoved.computeIfAbsent(uuid, t -> new HashMap<>()).put(id, landmark);
			}
		}));
		if (!landmarksRemoved.isEmpty()) SurveyorEvents.Invoke.landmarksRemoved(world, MapUtil.keyMultiMap(landmarksRemoved));
		if (!landmarksAddedChanged.isEmpty()) SurveyorEvents.Invoke.landmarksAdded(world, MapUtil.keyMultiMap(landmarksAddedChanged));
		if (!local) {
			Map<UUID, Map<Identifier, Landmark>> waypointsRemoved = new HashMap<>();
			Map<UUID, Map<Identifier, Landmark>> waypointsAddedChanged = new HashMap<>();
			landmarksRemoved.forEach((uuid, map) -> map.forEach((id, landmark) -> {
				if (!landmark.owner().equals(GLOBAL)) waypointsRemoved.computeIfAbsent(uuid, t -> new HashMap<>()).put(id, landmark);
			}));
			waypointsRemoved.forEach((uuid, map) -> map.forEach((id, landmark) -> {
				landmarksRemoved.get(uuid).remove(id);
				if (landmarksRemoved.get(uuid).isEmpty()) landmarksRemoved.remove(uuid);
			}));
			landmarksAddedChanged.forEach((uuid, map) -> map.forEach((id, landmark) -> {
				if (!landmark.owner().equals(GLOBAL)) waypointsAddedChanged.computeIfAbsent(uuid, t -> new HashMap<>()).put(id, landmark);
			}));
			waypointsAddedChanged.forEach((uuid, map) -> map.forEach((id, landmark) -> {
				landmarksAddedChanged.get(uuid).remove(id);
				if (landmarksAddedChanged.get(uuid).isEmpty()) landmarksAddedChanged.remove(uuid);
			}));

			if (!landmarksRemoved.isEmpty()) new SyncLandmarksRemovedPacket(MapUtil.keyMultiMap(landmarksRemoved)).send(sender, world, Surveyor.CONFIG.networking.landmarks);
			if (!landmarksAddedChanged.isEmpty()) new SyncLandmarksAddedPacket(landmarksAddedChanged).send(sender, world, Surveyor.CONFIG.networking.landmarks);
			if (!waypointsRemoved.isEmpty()) new SyncLandmarksRemovedPacket(MapUtil.keyMultiMap(waypointsRemoved)).send(sender, world, Surveyor.CONFIG.networking.waypoints);
			if (!waypointsAddedChanged.isEmpty()) new SyncLandmarksAddedPacket(waypointsAddedChanged).send(sender, world, Surveyor.CONFIG.networking.waypoints);
		}
	}

	public Map<UUID, Map<Identifier, Landmark>> putForBatch(Map<UUID, Map<Identifier, Landmark>> changed, Landmark landmark) {
		if (Surveyor.CONFIG.landmarks == SystemMode.FROZEN) return changed;
		landmarks.computeIfAbsent(landmark.owner(), t -> new ConcurrentHashMap<>()).put(landmark.id(), landmark);
		if (removed != null) removed.remove(landmark.owner(), landmark.id());
		dirty();
		changed.computeIfAbsent(landmark.owner(), t -> new HashMap<>()).put(landmark.id(), landmark);
		return changed;
	}

	public void putLocal(World world, Landmark landmark) {
		handleChanged(world, putForBatch(new HashMap<>(), landmark), true, null);
	}

	public void put(World world, Landmark landmark) {
		handleChanged(world, putForBatch(new HashMap<>(), landmark), false, null);
	}

	public void put(ServerPlayerEntity sender, ServerWorld world, Landmark landmark) {
		handleChanged(world, putForBatch(new HashMap<>(), landmark), false, sender);
	}

	public Map<UUID, Map<Identifier, Landmark>> removeForBatch(Map<UUID, Map<Identifier, Landmark>> changed, UUID uuid, Identifier id) {
		if (Surveyor.CONFIG.landmarks == SystemMode.FROZEN) return changed;
		if (!landmarks.containsKey(uuid) || !landmarks.get(uuid).containsKey(id)) return changed;
		Landmark landmark = landmarks.get(uuid).remove(id);
		if (removed != null) removed.put(uuid, id);
		if (landmarks.get(uuid).isEmpty()) landmarks.remove(uuid);
		dirty();
		changed.computeIfAbsent(uuid, t -> new HashMap<>()).put(id, landmark);
		return changed;
	}

	public void removeLocal(World world, UUID uuid, Identifier id) {
		handleChanged(world, removeForBatch(new HashMap<>(), uuid, id), true, null);
	}

	public void remove(World world, UUID uuid, Identifier id) {
		handleChanged(world, removeForBatch(new HashMap<>(), uuid, id), false, null);
	}

	public void remove(ServerPlayerEntity sender, ServerWorld world, UUID uuid, Identifier id) {
		handleChanged(world, removeForBatch(new HashMap<>(), uuid, id), false, sender);
	}

	public Map<UUID, Map<Identifier, Landmark>> removeAllForBatch(Map<UUID, Map<Identifier, Landmark>> changed, Predicate<Landmark> predicate) {
		if (Surveyor.CONFIG.landmarks == SystemMode.FROZEN) return null;
		Multimap<UUID, Identifier> toRemove = HashMultimap.create();
		landmarks.forEach((uuid, map) -> map.forEach((id, landmark) -> {
			if (predicate.test(landmark)) toRemove.put(uuid, id);
		}));
		toRemove.forEach((uuid, id) -> removeForBatch(changed, uuid, id));
		return changed;
	}

	public void removeAll(World world, Predicate<Landmark> predicate) {
		handleChanged(world, removeAllForBatch(new HashMap<>(), predicate), false, null);
	}

	public int save(World world, File folder) {
		if (isDirty()) {
			File landmarksFile = new File(folder, "landmarks.dat");
			NbtCompound landmarksCompound = writeNbt(new NbtCompound());
			Util.getIoWorkerExecutor().execute(() -> {
				try {
					NbtIo.writeCompressed(landmarksCompound, landmarksFile);
				} catch (IOException e) {
					Surveyor.LOGGER.error("[Surveyor] Error writing landmarks file for {}.", world.getRegistryKey().getValue(), e);
				}
			});
			dirty = false;
			return landmarks.values().stream().mapToInt(Map::size).sum();
		}
		return 0;
	}

	public void readUpdatePacket(World world, SyncLandmarksAddedPacket packet, @Nullable ServerPlayerEntity sender) {
		Map<UUID, Map<Identifier, Landmark>> changed = new HashMap<>();
		packet.landmarks().forEach((uuid, map) -> map.forEach((id, landmark) -> {
			boolean waypoint = !landmark.owner().equals(GLOBAL);
			boolean owned = sender == null || Surveyor.getUuid(sender).equals(landmark.owner());
			if (owned && (waypoint && Surveyor.CONFIG.networking.waypoints.atLeast(NetworkMode.SOLO) || !waypoint && Surveyor.CONFIG.networking.landmarks.atLeast(NetworkMode.SOLO))) {
				putForBatch(changed, landmark);
			}
		}));
		if (!changed.isEmpty()) handleChanged(world, changed, sender == null, sender);
	}

	public void readUpdatePacket(World world, SyncLandmarksRemovedPacket packet, @Nullable ServerPlayerEntity sender) {
		Map<UUID, Map<Identifier, Landmark>> changed = new HashMap<>();
		packet.landmarks().forEach((uuid, id) -> {
			Landmark landmark = get(uuid, id);
			if (landmark == null) return;
			boolean waypoint = !landmark.owner().equals(GLOBAL);
			boolean owned = sender == null || Surveyor.getUuid(sender).equals(landmark.owner());
			if (owned && ((waypoint && Surveyor.CONFIG.networking.waypoints.atLeast(NetworkMode.SOLO)) || (!waypoint && Surveyor.CONFIG.networking.landmarks.atLeast(NetworkMode.SOLO)))) {
				removeForBatch(changed, uuid, id);
			}
		});
		if (!changed.isEmpty()) handleChanged(world, changed, sender == null, sender);
	}

	public SyncLandmarksAddedPacket createUpdatePacket(Multimap<UUID, Identifier> keySet) {
		Map<UUID, Map<Identifier, Landmark>> landmarks = new HashMap<>();
		keySet.forEach((uuid, id) -> landmarks.computeIfAbsent(uuid, k -> new HashMap<>()).put(id, get(uuid, id)));
		return new SyncLandmarksAddedPacket(landmarks);
	}

	public boolean isDirty() {
		return dirty && Surveyor.CONFIG.landmarks != SystemMode.FROZEN;
	}

	private void dirty() {
		dirty = true;
	}

	private void tryMigrateXaeros(World world, boolean handleChanged) {
		if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;
		File saveFolder = SurveyorClient.getXaerosSavePath(world);
		if (saveFolder != null) {
			Map<UUID, Map<Identifier, Landmark>> changed = new HashMap<>();
			Surveyor.LOGGER.info("[Surveyor] Attempting to parse xaero's waypoints from {}/{}", saveFolder.getParentFile().getName(), saveFolder.getName());
			try {
				Files.createFile(saveFolder.toPath().resolve(".surveyor_migrated"));
				for (File file : Objects.requireNonNullElse(saveFolder.listFiles(f -> f.getName().endsWith(".txt")), new File[0])) {
					for (String line : Files.readAllLines(file.toPath())) {
						try {
							if (line.startsWith("waypoint:")) {
								String[] split = line.split(":");
								BlockPos pos = new BlockPos(Integer.parseInt(split[3]), split[4].equals("~") ? 0 : Integer.parseInt(split[4]), Integer.parseInt(split[5]));
								Identifier id = Identifier.of("xaeros", "waypoint/%d/%d/%d".formatted(pos.getX(), pos.getY(), pos.getZ()));
								putForBatch(changed, Landmark.create(SurveyorClient.getClientUuid(), id, b -> b
									.add(LandmarkComponentTypes.POS, pos)
									.add(LandmarkComponentTypes.COLOR, XAERO_COLORS[Integer.parseInt(split[6])])
									.add(LandmarkComponentTypes.NAME, Text.literal(split[1]))
								));
								dirty = true;
							}
						} catch (Exception e) {
							Surveyor.LOGGER.error("[Surveyor] Error parsing xaeros waypoint: {}", line, e);
						}
					}
				}
			} catch (Exception e) {
				Surveyor.LOGGER.error("[Surveyor] Error parsing xaeros data from {}", saveFolder, e);
			}
			Surveyor.LOGGER.info("[Surveyor] Migrated {} waypoints from xaeros data.", changed.values().stream().mapToInt(Map::size).sum());
			if (handleChanged) handleChanged(world, changed, Surveyor.CONFIG.networking.landmarks.atMost(NetworkMode.SOLO), null);
		}
	}

	public void clientInitialized(World world) {
		tryMigrateXaeros(world, true);
		if (Surveyor.CONFIG.networking.landmarks.atLeast(NetworkMode.SOLO)) new C2SKnownLandmarksPacket(keySet(null)).send();
	}
}
