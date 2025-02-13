package folk.sisby.surveyor;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import folk.sisby.surveyor.client.SurveyorClientEvents;
import folk.sisby.surveyor.config.NetworkMode;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.terrain.RegionSummary;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public interface SurveyorExploration {
	String KEY_EXPLORED_TERRAIN = "exploredTerrain";
	String KEY_EXPLORED_STRUCTURES = "exploredStructures";

	static SurveyorExploration of(ServerPlayerEntity player) {
		return PlayerSummary.of(player).exploration();
	}

	static SurveyorExploration of(UUID player, MinecraftServer server) {
		return ServerSummary.of(server).getExploration(player, server);
	}

	static SurveyorExploration ofShared(ServerPlayerEntity player) {
		return ofShared(Surveyor.getUuid(player), player.getServer());
	}

	static SurveyorExploration ofShared(UUID player, MinecraftServer server) {
		return ServerSummary.of(server).groupExploration(player, server);
	}

	Map<RegistryKey<World>, Map<ChunkPos, BitSet>> terrain();

	Map<RegistryKey<World>, Map<RegistryKey<Structure>, LongSet>> structures();

	Set<UUID> sharedPlayers();

	default void copyFrom(SurveyorExploration oldExploration) {
		terrain().putAll(oldExploration.terrain());
		structures().putAll(oldExploration.structures());
	}

	boolean personal();

	default boolean exploredChunk(RegistryKey<World> worldKey, ChunkPos pos) {
		ChunkPos regionPos = new ChunkPos(pos.getRegionX(), pos.getRegionZ());
		Map<ChunkPos, BitSet> regions = terrain().get(worldKey);
		return (!personal() && Surveyor.CONFIG.networking.terrain.atLeast(NetworkMode.SERVER)) || regions != null && regions.containsKey(regionPos) && regions.get(regionPos).get(RegionSummary.bitForChunk(pos));
	}

	default boolean exploredStructure(RegistryKey<World> worldKey, RegistryKey<Structure> structure, ChunkPos pos) {
		Map<RegistryKey<Structure>, LongSet> structures = structures().get(worldKey);
		return (!personal() && Surveyor.CONFIG.networking.structures.atLeast(NetworkMode.SERVER)) || structures != null && structures.containsKey(structure) && structures.get(structure).contains(pos.toLong());
	}

	default boolean exploredLandmark(RegistryKey<World> worldKey, Landmark landmark) {
		return landmark.owner() == null ? !landmark.components().contains(LandmarkComponentTypes.POS) || exploredChunk(worldKey, new ChunkPos(landmark.components().get(LandmarkComponentTypes.POS))) : sharedPlayers().contains(landmark.owner());
	}

	default int chunkCount() {
		return terrain().values().stream().flatMap(m -> m.values().stream()).mapToInt(BitSet::cardinality).sum();
	}

	default int structureCount() {
		return structures().values().stream().flatMap(m -> m.values().stream()).mapToInt(LongSet::size).sum();
	}

	default BitSet limitTerrainBitset(RegistryKey<World> worldKey, ChunkPos rPos, BitSet bitSet) {
		if (!personal() && Surveyor.CONFIG.networking.terrain.atLeast(NetworkMode.SERVER)) return bitSet;
		if (terrain().get(worldKey) == null || !terrain().get(worldKey).containsKey(rPos)) {
			bitSet.clear();
		} else {
			bitSet.and(terrain().get(worldKey).get(rPos));
		}
		return bitSet;
	}

	default Map<ChunkPos, BitSet> limitTerrainBitset(RegistryKey<World> worldKey, Map<ChunkPos, BitSet> bitSet) {
		if (!personal() && Surveyor.CONFIG.networking.terrain.atLeast(NetworkMode.SERVER)) return bitSet;
		Map<ChunkPos, BitSet> regions = terrain().get(worldKey);
		if (regions == null) {
			bitSet.clear();
		} else {
			bitSet.forEach((rPos, set) -> limitTerrainBitset(worldKey, rPos, set));
		}
		return bitSet;
	}

	default Multimap<RegistryKey<Structure>, ChunkPos> limitStructureKeySet(RegistryKey<World> worldKey, Multimap<RegistryKey<Structure>, ChunkPos> keySet) {
		if (!personal() && Surveyor.CONFIG.networking.structures.atLeast(NetworkMode.SERVER)) return keySet;
		Map<RegistryKey<Structure>, LongSet> structures = structures().get(worldKey);
		if (structures == null) {
			keySet.clear();
		} else {
			keySet.keySet().removeIf(key -> !structures.containsKey(key));
			keySet.entries().removeIf(e -> !structures.get(e.getKey()).contains(e.getValue().toLong()));
		}
		return keySet;
	}

	default Map<UUID, Map<Identifier, Landmark>> limitLandmarkMap(RegistryKey<World> worldKey, Map<UUID, Map<Identifier, Landmark>> asMap) {
		Multimap<UUID, Identifier> toRemove = HashMultimap.create();
		asMap.forEach((uuid, map) -> map.forEach((id, landmark) -> {
			if (!exploredLandmark(worldKey, landmark)) toRemove.put(uuid, id);
		}));
		toRemove.forEach((uuid, id) -> {
			asMap.get(uuid).remove(id);
			if (asMap.get(uuid).isEmpty()) asMap.remove(uuid);
		});
		return asMap;
	}

	default Multimap<UUID, Identifier> limitLandmarkKeySet(RegistryKey<World> worldKey, WorldLandmarks worldLandmarks, Multimap<UUID, Identifier> keySet) {
		Multimap<UUID, Identifier> toRemove = HashMultimap.create();
		keySet.forEach((uuid, id) -> {
			if (!worldLandmarks.contains(uuid, id) || !exploredLandmark(worldKey, worldLandmarks.get(uuid, id))) toRemove.put(uuid, id);
		});
		toRemove.forEach(keySet::remove);
		return keySet;
	}

	default void updateClientForMergeRegion(World world, ChunkPos regionPos, BitSet bitSet) {
		Set<ChunkPos> terrainKeys = bitSet.stream().mapToObj(i -> RegionSummary.chunkForBit(regionPos, i)).collect(Collectors.toSet());
		SurveyorClientEvents.Invoke.terrainUpdated(world, terrainKeys);
		Multimap<UUID, Identifier> landmarkKeys = HashMultimap.create();
		WorldLandmarks summary = WorldSummary.of(world).landmarks();
		if (summary == null) return;
		summary.asMap(this).forEach((uuid, map) -> map.forEach((id, landmark) -> {
			if (landmark.components().contains(LandmarkComponentTypes.POS) && terrainKeys.contains(new ChunkPos(landmark.components().get(LandmarkComponentTypes.POS))) && landmark.owner() == null) landmarkKeys.put(uuid, id);
		}));
		SurveyorClientEvents.Invoke.landmarksAdded(world, landmarkKeys);
	}

	default void updateClientForLandmarks(World world) {
		WorldLandmarks summary = WorldSummary.of(world).landmarks();
		if (summary == null) return;
		Multimap<UUID, Identifier> unexploredLandmarks = summary.keySet(null);
		Multimap<UUID, Identifier> exploredLandmarks = summary.keySet(this);
		exploredLandmarks.forEach(unexploredLandmarks::remove);
		SurveyorClientEvents.Invoke.landmarksAdded(world, exploredLandmarks);
		SurveyorClientEvents.Invoke.landmarksRemoved(world, unexploredLandmarks);
	}

	default void mergeRegion(RegistryKey<World> worldKey, ChunkPos regionPos, BitSet bitSet) {
		terrain().computeIfAbsent(worldKey, k -> new HashMap<>()).computeIfAbsent(regionPos, p -> new BitSet(RegionSummary.REGION_SIZE)).or(bitSet);
	}

	default void replaceTerrain(RegistryKey<World> worldKey, Map<ChunkPos, BitSet> bitSet) {
		Map<ChunkPos, BitSet> oldSet = terrain().get(worldKey);
		if (oldSet != null) oldSet.clear();
		bitSet.forEach((pos, set) -> mergeRegion(worldKey, pos, set));
	}

	default void updateClientForAddChunk(World world, ChunkPos chunkPos) {
		SurveyorClientEvents.Invoke.terrainUpdated(world, chunkPos);
		Multimap<UUID, Identifier> landmarkKeys = HashMultimap.create();
		WorldLandmarks summary = WorldSummary.of(world).landmarks();
		if (summary == null) return;
		summary.asMap(this).forEach((uuid, map) -> map.forEach((id, landmark) -> {
			if (landmark.components().contains(LandmarkComponentTypes.POS) && chunkPos.equals(new ChunkPos(landmark.components().get(LandmarkComponentTypes.POS))) && landmark.owner() == null) landmarkKeys.put(uuid, id);
		}));
		SurveyorClientEvents.Invoke.landmarksAdded(world, landmarkKeys);
	}

	default void addChunk(RegistryKey<World> worldKey, ChunkPos pos) {
		terrain().computeIfAbsent(worldKey, k -> new HashMap<>()).computeIfAbsent(new ChunkPos(pos.getRegionX(), pos.getRegionZ()), k -> new BitSet(RegionSummary.BITSET_SIZE)).set(RegionSummary.bitForChunk(pos));
	}

	default void updateClientForAddStructure(World world, RegistryKey<Structure> structureKey, ChunkPos pos) {
		SurveyorClientEvents.Invoke.structuresAdded(world, structureKey, pos);
	}

	default void addStructure(RegistryKey<World> worldKey, RegistryKey<Structure> structureKey, ChunkPos pos) {
		structures().computeIfAbsent(worldKey, k -> new HashMap<>()).computeIfAbsent(structureKey, s -> new LongOpenHashSet()).add(pos.toLong());
	}

	default void mergeStructures(RegistryKey<World> worldKey, RegistryKey<Structure> structureKey, LongSet starts) {
		structures().computeIfAbsent(worldKey, k -> new HashMap<>()).computeIfAbsent(structureKey, s -> new LongOpenHashSet()).addAll(starts);
	}

	default void replaceStructures(RegistryKey<World> worldKey, Map<RegistryKey<Structure>, LongSet> structures) {
		LongSet oldSet = structures.get(worldKey);
		if (oldSet != null) oldSet.clear();
		structures.forEach((key, set) -> mergeStructures(worldKey, key, set));
	}

	default NbtCompound write(NbtCompound nbt) {
		NbtCompound terrainCompound = new NbtCompound();
		terrain().forEach((worldKey, map) -> {
			LongList regionLongs = new LongArrayList();
			for (Map.Entry<ChunkPos, BitSet> entry : map.entrySet()) {
				regionLongs.add(entry.getKey().toLong());
				if (entry.getValue().cardinality() == RegionSummary.BITSET_SIZE) {
					regionLongs.add(-1);
				} else {
					long[] regionBits = entry.getValue().toLongArray();
					regionLongs.add(regionBits.length);
					regionLongs.addAll(LongList.of(regionBits));
				}
			}
			terrainCompound.putLongArray(worldKey.getValue().toString(), regionLongs.toLongArray());
		});
		nbt.put(KEY_EXPLORED_TERRAIN, terrainCompound);

		NbtCompound structuresCompound = new NbtCompound();
		structures().forEach((worldKey, map) -> {
			NbtCompound worldStructuresCompound = new NbtCompound();
			for (RegistryKey<Structure> structure : map.keySet()) {
				worldStructuresCompound.putLongArray(structure.getValue().toString(), map.get(structure).toLongArray());
			}
			structuresCompound.put(worldKey.getValue().toString(), worldStructuresCompound);
		});
		nbt.put(KEY_EXPLORED_STRUCTURES, structuresCompound);
		return nbt;
	}

	default void read(NbtCompound nbt) {
		NbtCompound terrainCompound = nbt.getCompound(KEY_EXPLORED_TERRAIN);
		for (String worldKeyString : terrainCompound.getKeys()) {
			long[] regionArray = terrainCompound.getLongArray(worldKeyString);
			Map<ChunkPos, BitSet> regionMap = new HashMap<>();
			for (int i = 0; i + 1 < regionArray.length; i += 2) {
				ChunkPos rPos = new ChunkPos(regionArray[i]);
				int bitLength = (int) regionArray[i + 1];
				if (bitLength == -1) {
					BitSet set = new BitSet(RegionSummary.BITSET_SIZE);
					set.set(0, RegionSummary.BITSET_SIZE);
					regionMap.put(rPos, set);
				} else {
					long[] bitArray = new long[bitLength];
					System.arraycopy(regionArray, i + 2, bitArray, 0, bitLength);
					regionMap.put(rPos, BitSet.valueOf(bitArray));
					i += bitLength;
				}
			}
			terrain().put(RegistryKey.of(RegistryKeys.WORLD, new Identifier(worldKeyString)), regionMap);
		}

		NbtCompound structuresCompound = nbt.getCompound(KEY_EXPLORED_STRUCTURES);
		for (String worldKeyString : structuresCompound.getKeys()) {
			Map<RegistryKey<Structure>, LongSet> structureMap = new HashMap<>();
			NbtCompound worldStructuresCompound = structuresCompound.getCompound(worldKeyString);
			for (String key : worldStructuresCompound.getKeys()) {
				structureMap.put(RegistryKey.of(RegistryKeys.STRUCTURE, new Identifier(key)), new LongOpenHashSet(worldStructuresCompound.getLongArray(key)));
			}
			structures().put(RegistryKey.of(RegistryKeys.WORLD, new Identifier(worldKeyString)), structureMap);
		}
	}
}
