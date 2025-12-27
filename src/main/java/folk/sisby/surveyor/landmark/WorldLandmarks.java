package folk.sisby.surveyor.landmark;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import folk.sisby.surveyor.ServerSummary;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.SurveyorEvents;
import folk.sisby.surveyor.SurveyorExploration;
import folk.sisby.surveyor.WorldSummary;
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
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
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
import java.util.function.Predicate;

public class WorldLandmarks {
	public static final UUID GLOBAL = UUID.fromString("99999999-9999-9999-9999-999999999999");
	public static final String KEY_LANDMARKS = "landmarks";
	public static final String KEY_REMOVED = "removed";
	public static final Codec<Table<UUID, Identifier, Landmark>> CODEC = DispatchMapCodec.of(
		Uuids.STRING_CODEC,
		uuid -> DispatchMapCodec.of(
			Identifier.CODEC,
			id -> Landmark.createCodec(uuid, id)
		)
	).xmap(MapUtil::asTable, Table::rowMap);
	public static final Codec<Multimap<UUID, Identifier>> REMOVED_CODEC = Codec.unboundedMap(
		Uuids.STRING_CODEC,
		Codec.list(Identifier.CODEC)
	).xmap(MapUtil::asMultiMap, MapUtil::asListMap);
	public static final int[] XAERO_COLORS = new int[]{
		0xFF_000000, 0xFF_0000AA, 0xFF_00AA00, 0xFF_00AAAA, 0xFF_AA0000, 0xFF_AA00AA, 0xFF_ffAA00, 0xFF_AAAAAA, 0xFF_555555, 0xFF_5555FF, 0xFF_55FF55, 0xFF_55FFFF, 0xFF_FF0000, 0xFF_FF55FF, 0xFF_FFFF55, 0xFF_FFFFFF
	};

	protected final WorldSummary summary;
	protected final Table<UUID, Identifier, Landmark> landmarks = Tables.synchronizedTable(HashBasedTable.create());
	protected final @Nullable Multimap<UUID, Identifier> removed;
	protected boolean dirty;

	public WorldLandmarks(WorldSummary summary, Table<UUID, Identifier, Landmark> landmarks, Multimap<UUID, Identifier> removed, boolean dirty) {
		this.summary = summary;
		this.landmarks.putAll(landmarks);
		this.removed = removed == null ? null : Multimaps.synchronizedSetMultimap(HashMultimap.create(removed));
		if (this.removed != null) this.landmarks.cellSet().forEach(c -> removed.remove(c.getRowKey(), c.getColumnKey()));
		this.dirty = dirty;
	}

	public static WorldLandmarks load(WorldSummary summary, DynamicRegistryManager manager, File folder) {
		NbtCompound landmarkNbt = new NbtCompound();
		File landmarksFile = new File(folder, "landmarks.dat");
		if (landmarksFile.exists()) {
			try {
				landmarkNbt = NbtIo.readCompressed(landmarksFile);
			} catch (IOException | CrashException e) {
				Surveyor.LOGGER.error("[Surveyor] Error loading landmarks file for {}.", summary.dimension().getValue(), e);
			}
		}
		WorldLandmarks worldLandmarks = fromNbt(summary, manager, landmarkNbt, landmarksFile);
		if (!summary.isClient()) worldLandmarks.tryMigrateXaeros(false); // for singleplayer
		return worldLandmarks;
	}

	public NbtCompound writeNbt(NbtCompound nbt) {
		nbt.put(KEY_LANDMARKS, CODEC.encodeStart(NbtOps.INSTANCE, landmarks).resultOrPartial(Surveyor.LOGGER::error).orElseThrow());
		if (removed != null) nbt.put(KEY_REMOVED, REMOVED_CODEC.encodeStart(NbtOps.INSTANCE, removed).resultOrPartial(Surveyor.LOGGER::error).orElseThrow());
		return nbt;
	}

	public static WorldLandmarks fromNbt(WorldSummary summary, DynamicRegistryManager manager, NbtCompound nbt, File landmarksFile) {
		NbtCompound landmarks = nbt.getCompound(KEY_LANDMARKS);
		boolean dirty = false;
		Table<UUID, Identifier, Landmark> outMap = HashBasedTable.create();
		Multimap<UUID, Identifier> removedMap = summary.server() == null ? null : HashMultimap.create();
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
						outMap.put(owner, id, Landmark.create(owner, id, b -> b
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
				Surveyor.LOGGER.info("[Surveyor] Recovered {} landmarks from legacy data.", outMap.size());
			} catch (Exception e) {
				Surveyor.LOGGER.error("[Surveyor] Encountered an error during v0 landmark migration, skipping...", e);
			}
		} else {
			if (!landmarks.isEmpty()) outMap.putAll(CODEC.decode(NbtOps.INSTANCE, landmarks).resultOrPartial(Surveyor.LOGGER::error).orElseThrow().getFirst());
			if (!summary.isClient()) {
				NbtCompound removed = nbt.getCompound(KEY_REMOVED);
				if (!removed.isEmpty()) removedMap = REMOVED_CODEC.decode(NbtOps.INSTANCE, removed).resultOrPartial(Surveyor.LOGGER::error).orElseThrow().getFirst();
			}
		}
		return new WorldLandmarks(summary, outMap, removedMap, dirty);
	}

	public boolean contains(UUID uuid, Identifier id) {
		return landmarks.contains(uuid, id);
	}

	public @Nullable Landmark get(UUID uuid, Identifier id) {
		return contains(uuid, id) ? landmarks.get(uuid, id) : null;
	}

	public Map<Identifier, Landmark> asMap(UUID uuid, SurveyorExploration exploration) {
		Map<Identifier, Landmark> outMap = new HashMap<>(landmarks.row(uuid));
		if (exploration != null) outMap.values().removeIf(l -> !exploration.exploredLandmark(summary.dimension(), l));
		return outMap;
	}

	public Table<UUID, Identifier, Landmark> asMap(SurveyorExploration exploration) {
		Table<UUID, Identifier, Landmark> outMap = HashBasedTable.create(landmarks);
		if (exploration != null) outMap.values().removeIf(l -> !exploration.exploredLandmark(summary.dimension(), l));
		return outMap;
	}

	public Multimap<UUID, Identifier> keySet(SurveyorExploration exploration) {
		Multimap<UUID, Identifier> outMap = MapUtil.keyMultiMap(landmarks);
		if (exploration != null) outMap.entries().removeIf(e -> !exploration.exploredLandmark(summary.dimension(), landmarks.get(e.getKey(), e.getValue())));
		return outMap;
	}

	public Multimap<UUID, Identifier> removed() {
		return HashMultimap.create(Objects.requireNonNullElse(removed, HashMultimap.create()));
	}

	public void handleChanged(Table<UUID, Identifier, Landmark> changed, boolean local, @Nullable ServerPlayerEntity sender) {
		if (changed.isEmpty()) return;
		Table<UUID, Identifier, Landmark> landmarksAddedChanged = HashBasedTable.create(changed);
		Table<UUID, Identifier, Landmark> landmarksRemoved = HashBasedTable.create(changed);
		landmarksAddedChanged.cellSet().removeIf(c -> !contains(c.getRowKey(), c.getColumnKey()));
		landmarksAddedChanged.cellSet().forEach(c -> landmarksRemoved.remove(c.getRowKey(), c.getColumnKey()));
		if (!landmarksRemoved.isEmpty()) SurveyorEvents.Invoke.landmarksRemoved(summary, MapUtil.keyMultiMap(landmarksRemoved));
		if (!landmarksAddedChanged.isEmpty()) SurveyorEvents.Invoke.landmarksAdded(summary, MapUtil.keyMultiMap(landmarksAddedChanged));
		if (!local) {
			Table<UUID, Identifier, Landmark> waypointsRemoved = HashBasedTable.create(landmarksAddedChanged);
			Table<UUID, Identifier, Landmark> waypointsAddedChanged = HashBasedTable.create(landmarksRemoved);
			waypointsRemoved.rowKeySet().remove(WorldLandmarks.GLOBAL);
			waypointsAddedChanged.rowKeySet().remove(WorldLandmarks.GLOBAL);
			landmarksAddedChanged.rowKeySet().removeAll(waypointsAddedChanged.rowKeySet());
			landmarksRemoved.rowKeySet().removeAll(waypointsRemoved.rowKeySet());

			if (!landmarksRemoved.isEmpty()) new SyncLandmarksRemovedPacket(summary.dimension(), MapUtil.keyMultiMap(landmarksRemoved)).send(sender, summary, Surveyor.CONFIG.networking.landmarks);
			if (!landmarksAddedChanged.isEmpty()) new SyncLandmarksAddedPacket(summary.dimension(), landmarksAddedChanged).send(sender, summary, Surveyor.CONFIG.networking.landmarks);
			if (!waypointsRemoved.isEmpty()) new SyncLandmarksRemovedPacket(summary.dimension(), MapUtil.keyMultiMap(waypointsRemoved)).send(sender, summary, Surveyor.CONFIG.networking.waypoints);
			if (!waypointsAddedChanged.isEmpty()) new SyncLandmarksAddedPacket(summary.dimension(), waypointsAddedChanged).send(sender, summary, Surveyor.CONFIG.networking.waypoints);
		}
	}

	public Table<UUID, Identifier, Landmark> putForBatch(Table<UUID, Identifier, Landmark> changed, Landmark landmark) {
		if (Surveyor.CONFIG.landmarks == SystemMode.FROZEN) return changed;
		landmarks.put(landmark.owner(), landmark.id(), landmark);
		if (removed != null) removed.remove(landmark.owner(), landmark.id());
		dirty();
		changed.put(landmark.owner(), landmark.id(), landmark);
		return changed;
	}

	public Table<UUID, Identifier, Landmark> putForBatch(Landmark landmark) {
		return putForBatch(HashBasedTable.create(), landmark);
	}

	public void putLocal(Landmark landmark) {
		handleChanged(putForBatch(landmark), true, null);
	}

	public void put(Landmark landmark) {
		handleChanged(putForBatch(landmark), false, null);
	}

	public void put(ServerPlayerEntity sender, Landmark landmark) {
		handleChanged(putForBatch(landmark), false, sender);
	}

	public Table<UUID, Identifier, Landmark> removeForBatch(Table<UUID, Identifier, Landmark> changed, UUID uuid, Identifier id) {
		if (Surveyor.CONFIG.landmarks == SystemMode.FROZEN) return changed;
		if (!landmarks.contains(uuid, id)) return changed;
		Landmark landmark = landmarks.remove(uuid, id);
		if (removed != null) removed.put(uuid, id);
		dirty();
		changed.put(uuid, id, landmark);
		return changed;
	}

	public Table<UUID, Identifier, Landmark> removeForBatch(UUID uuid, Identifier id) {
		return removeForBatch(HashBasedTable.create(), uuid, id);
	}

	public void removeLocal(UUID uuid, Identifier id) {
		handleChanged(removeForBatch(uuid, id), true, null);
	}

	public void remove(UUID uuid, Identifier id) {
		handleChanged(removeForBatch(uuid, id), false, null);
	}

	public void remove(ServerPlayerEntity sender, UUID uuid, Identifier id) {
		handleChanged(removeForBatch(uuid, id), false, sender);
	}

	public Table<UUID, Identifier, Landmark> removeAllForBatch(Table<UUID, Identifier, Landmark> changed, Predicate<Landmark> predicate) {
		if (Surveyor.CONFIG.landmarks == SystemMode.FROZEN) return null;
		Table<UUID, Identifier, Landmark> toRemove = HashBasedTable.create(changed);
		toRemove.values().removeIf(predicate.negate());
		toRemove.cellSet().forEach(c -> removeForBatch(changed, c.getRowKey(), c.getColumnKey()));
		return changed;
	}

	public Table<UUID, Identifier, Landmark> removeAllForBatch(Predicate<Landmark> predicate) {
		return removeAllForBatch(HashBasedTable.create(), predicate);
	}

	public void removeAll(Predicate<Landmark> predicate) {
		handleChanged(removeAllForBatch(predicate), false, null);
	}

	public int save(File folder) {
		if (isDirty()) {
			File landmarksFile = new File(folder, "landmarks.dat");
			NbtCompound landmarksCompound = writeNbt(new NbtCompound());
			Util.getIoWorkerExecutor().execute(() -> {
				try {
					NbtIo.writeCompressed(landmarksCompound, landmarksFile);
				} catch (IOException e) {
					Surveyor.LOGGER.error("[Surveyor] Error writing landmarks file for {}.", summary.dimension().getValue(), e);
				}
			});
			dirty = false;
			return landmarks.size();
		}
		return 0;
	}

	public static boolean canModify(UUID landmark, World world, @Nullable ServerPlayerEntity player) {
		World serverWorld = world == null ? null : world.isClient() ? SurveyorClient.stealServerWorld(world.getRegistryKey()) : world;
		if (serverWorld == null) {
			return landmark.equals(SurveyorClient.getClientUuid()) || (Surveyor.CONFIG.networking.waypoints.atLeast(NetworkMode.GROUP) && SurveyorClient.getSharedExploration().groupPlayers().contains(landmark));
		} else {
			return player == null || player.hasPermissionLevel(2) ||  landmark.equals(Surveyor.getUuid(player)) || ServerSummary.of(serverWorld.getServer()).getGroup(Surveyor.getUuid(player)).contains(landmark);
		}
	}

	public Table<UUID, Identifier, Landmark> readUpdatePacket(World world, SyncLandmarksAddedPacket packet, @Nullable ServerPlayerEntity sender) {
		Table<UUID, Identifier, Landmark> changed = HashBasedTable.create();
		packet.landmarks().values().forEach(landmark -> {
			boolean waypoint = !landmark.owner().equals(GLOBAL);
			if (sender == null || canModify(landmark.owner(), world, sender) && (waypoint && Surveyor.CONFIG.networking.waypoints.atLeast(NetworkMode.SOLO) || !waypoint && Surveyor.CONFIG.networking.landmarks.atLeast(NetworkMode.SOLO))) {
				putForBatch(changed, landmark);
			}
		});
		if (!changed.isEmpty()) handleChanged(changed, sender == null, sender);
		return changed;
	}

	public Table<UUID, Identifier, Landmark>  readUpdatePacket(World world, SyncLandmarksRemovedPacket packet, @Nullable ServerPlayerEntity sender) {
		Table<UUID, Identifier, Landmark> changed = HashBasedTable.create();
		packet.landmarks().forEach((uuid, id) -> {
			Landmark landmark = get(uuid, id);
			if (landmark == null) return;
			boolean waypoint = !landmark.owner().equals(GLOBAL);
			if (sender == null || canModify(landmark.owner(), world, sender) && ((waypoint && Surveyor.CONFIG.networking.waypoints.atLeast(NetworkMode.SOLO)) || (!waypoint && Surveyor.CONFIG.networking.landmarks.atLeast(NetworkMode.SOLO)))) {
				removeForBatch(changed, uuid, id);
			}
		});
		if (!changed.isEmpty()) handleChanged(changed, sender == null, sender);
		return changed;
	}

	public SyncLandmarksAddedPacket createUpdatePacket(Multimap<UUID, Identifier> keySet) {
		Table<UUID, Identifier, Landmark> updated = HashBasedTable.create();
		keySet.forEach((uuid, id) -> updated.put(uuid, id, get(uuid, id)));
		return new SyncLandmarksAddedPacket(summary.dimension(), updated);
	}

	public boolean isDirty() {
		return dirty && Surveyor.CONFIG.landmarks != SystemMode.FROZEN;
	}

	private void dirty() {
		dirty = true;
	}

	private void tryMigrateXaeros(boolean handleChanged) {
		if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;
		File saveFolder = SurveyorClient.getXaerosSavePath(summary.dimension());
		if (saveFolder != null) {
			Table<UUID, Identifier, Landmark> changed = HashBasedTable.create();
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
			Surveyor.LOGGER.info("[Surveyor] Migrated {} waypoints from xaeros data.", changed.size());
			if (handleChanged) handleChanged(changed, Surveyor.CONFIG.networking.landmarks.atMost(NetworkMode.SOLO), null);
		}
	}

	public void clientInitialized(DynamicRegistryManager manager) {
		tryMigrateXaeros(true);
		if (Surveyor.CONFIG.networking.landmarks.atLeast(NetworkMode.SOLO)) C2SKnownLandmarksPacket.of(summary.dimension(), keySet(null)).send();
	}
}
