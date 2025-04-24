package folk.sisby.surveyor.landmark;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.SurveyorEvents;
import folk.sisby.surveyor.SurveyorExploration;
import folk.sisby.surveyor.config.NetworkMode;
import folk.sisby.surveyor.config.SystemMode;
import folk.sisby.surveyor.packet.SyncLandmarksAddedPacket;
import folk.sisby.surveyor.packet.SyncLandmarksRemovedPacket;
import folk.sisby.surveyor.util.MapUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.crash.CrashException;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public class WorldLandmarks {
	public static final UUID GLOBAL = UUID.fromString("99999999-9999-9999-9999-999999999999");
	protected final RegistryKey<World> worldKey;
	protected final Map<UUID, Map<Identifier, Landmark>> landmarks = new ConcurrentHashMap<>();
	protected boolean dirty = false;

	public WorldLandmarks(RegistryKey<World> worldKey, Map<UUID, Map<Identifier, Landmark>> landmarks) {
		this.worldKey = worldKey;
		this.landmarks.putAll(landmarks);
	}

	public static WorldLandmarks load(World world, File folder) {
		NbtCompound landmarkNbt = new NbtCompound();
		File landmarksFile = new File(folder, "landmarks.dat");
		if (landmarksFile.exists()) {
			try {
				landmarkNbt = NbtIo.readCompressed(landmarksFile);
			} catch (IOException | CrashException e) {
				Surveyor.LOGGER.error("[Surveyor] Error loading landmarks file for {}.", world.getRegistryKey().getValue(), e);
			}
		}
		var landmarks = Landmarks.fromNbt(landmarkNbt);
		return new WorldLandmarks(world.getRegistryKey(), landmarks);
	}

	public boolean contains(UUID uuid, Identifier id) {
		return landmarks.containsKey(uuid) && landmarks.get(uuid).containsKey(id);
	}

	public Landmark get(UUID uuid, Identifier id) {
		return landmarks.get(uuid).get(id);
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

	public void handleChanged(World world, Map<UUID, Map<Identifier, Landmark>> changed, boolean local, @Nullable ServerPlayerEntity sender) {
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
		dirty();
		changed.computeIfAbsent(landmark.owner(), t -> new HashMap<>()).put(landmark.id(), landmark);
		return changed;
	}

	public void putLocal(World world, Landmark landmark) {
		if (Surveyor.CONFIG.landmarks == SystemMode.FROZEN) return;
		Map<UUID, Map<Identifier, Landmark>> changed = putForBatch(new HashMap<>(), landmark);
		handleChanged(world, changed, true, null);
	}

	public void put(World world, Landmark landmark) {
		if (Surveyor.CONFIG.landmarks == SystemMode.FROZEN) return;
		Map<UUID, Map<Identifier, Landmark>> changed = putForBatch(new HashMap<>(), landmark);
		handleChanged(world, changed, false, null);
	}

	public void put(ServerPlayerEntity sender, ServerWorld world, Landmark landmark) {
		if (Surveyor.CONFIG.landmarks == SystemMode.FROZEN) return;
		Map<UUID, Map<Identifier, Landmark>> changed = putForBatch(new HashMap<>(), landmark);
		handleChanged(world, changed, false, sender);
	}

	public Map<UUID, Map<Identifier, Landmark>> removeForBatch(Map<UUID, Map<Identifier, Landmark>> changed, UUID uuid, Identifier id) {
		if (Surveyor.CONFIG.landmarks == SystemMode.FROZEN) return changed;
		if (!landmarks.containsKey(uuid) || !landmarks.get(uuid).containsKey(id)) return changed;
		Landmark landmark = landmarks.get(uuid).remove(id);
		if (landmarks.get(uuid).isEmpty()) landmarks.remove(uuid);
		dirty();
		changed.computeIfAbsent(uuid, t -> new HashMap<>()).put(id, landmark);
		return changed;
	}

	public void removeLocal(World world, UUID uuid, Identifier id) {
		if (Surveyor.CONFIG.landmarks == SystemMode.FROZEN) return;
		if (!landmarks.containsKey(uuid) || !landmarks.get(uuid).containsKey(id)) return;
		Landmark landmark = landmarks.get(uuid).get(id);
		Map<UUID, Map<Identifier, Landmark>> changed = removeForBatch(new HashMap<>(), landmark.owner(), landmark.id());
		handleChanged(world, changed, true, null);
	}

	public void remove(World world, UUID uuid, Identifier id) {
		if (Surveyor.CONFIG.landmarks == SystemMode.FROZEN) return;
		if (!landmarks.containsKey(uuid) || !landmarks.get(uuid).containsKey(id)) return;
		Landmark landmark = landmarks.get(uuid).get(id);
		Map<UUID, Map<Identifier, Landmark>> changed = removeForBatch(new HashMap<>(), landmark.owner(), landmark.id());
		handleChanged(world, changed, false, null);
	}

	public void remove(ServerPlayerEntity sender, ServerWorld world, UUID uuid, Identifier id) {
		if (Surveyor.CONFIG.landmarks == SystemMode.FROZEN) return;
		if (!landmarks.containsKey(uuid) || !landmarks.get(uuid).containsKey(id)) return;
		Landmark landmark = landmarks.get(uuid).get(id);
		Map<UUID, Map<Identifier, Landmark>> changed = removeForBatch(new HashMap<>(), landmark.owner(), landmark.id());
		handleChanged(world, changed, false, sender);
	}

	public void removeAll(World world, Predicate<Landmark> predicate) {
		if (Surveyor.CONFIG.landmarks == SystemMode.FROZEN) return;
		Map<UUID, Map<Identifier, Landmark>> changed = new HashMap<>();
		Multimap<UUID, Identifier> toRemove = HashMultimap.create();
		landmarks.forEach((uuid, map) -> map.forEach((id, landmark) -> {
			if (predicate.test(landmark)) toRemove.put(uuid, id);
		}));
		toRemove.forEach((uuid, id) -> removeForBatch(changed, uuid, id));
		handleChanged(world, changed, false, null);
	}

	public int save(World world, File folder) {
		if (isDirty()) {
			File landmarksFile = new File(folder, "landmarks.dat");
			NbtCompound landmarksCompound = Landmarks.writeNbt(landmarks, new NbtCompound());
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
			if (!contains(uuid, id)) return;
			Landmark landmark = get(uuid, id);
			boolean waypoint = !landmark.owner().equals(GLOBAL);
			boolean owned = sender == null || Surveyor.getUuid(sender).equals(landmark.owner());
			if (owned && (waypoint && Surveyor.CONFIG.networking.waypoints.atLeast(NetworkMode.SOLO) || !waypoint && Surveyor.CONFIG.networking.landmarks.atLeast(NetworkMode.SOLO))) {
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
}
