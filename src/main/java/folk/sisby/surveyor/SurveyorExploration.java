package folk.sisby.surveyor;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import folk.sisby.surveyor.client.SurveyorClientEvents;
import folk.sisby.surveyor.config.NetworkMode;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.util.RegionPos;
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

import java.util.*;
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

	Table<RegistryKey<World>, RegionPos, BitSet> terrain();

	Table<RegistryKey<World>, RegistryKey<Structure>, LongSet> structures();

	Set<UUID> sharedPlayers();

	default void copyFrom(SurveyorExploration oldExploration) {
		terrain().putAll(oldExploration.terrain());
		structures().putAll(oldExploration.structures());
	}

	boolean personal();

	default boolean exploredChunk(RegistryKey<World> worldKey, ChunkPos pos) {
		RegionPos regionPos = RegionPos.of(pos);
		return !personal() && Surveyor.CONFIG.networking.terrain.atLeast(NetworkMode.SERVER) || terrain().contains(worldKey, regionPos) && terrain().get(worldKey, regionPos).get(RegionPos.chunkToBit(pos));
	}

	default boolean exploredStructure(RegistryKey<World> worldKey, RegistryKey<Structure> structure, ChunkPos pos) {
		return !personal() && Surveyor.CONFIG.networking.structures.atLeast(NetworkMode.SERVER) || structures().contains(worldKey, structure) && structures().get(worldKey, structure).contains(pos.toLong());
	}

	default boolean exploredLandmark(RegistryKey<World> worldKey, Landmark landmark) {
		return landmark.owner().equals(WorldLandmarks.GLOBAL) ? !landmark.components().contains(LandmarkComponentTypes.POS) || exploredChunk(worldKey, new ChunkPos(landmark.components().get(LandmarkComponentTypes.POS))) : sharedPlayers().contains(landmark.owner());
	}

	default int chunkCount() {
		return terrain().values().stream().mapToInt(BitSet::cardinality).sum();
	}

	default int structureCount() {
		return structures().values().stream().mapToInt(LongSet::size).sum();
	}

	default BitSet limitTerrainBitset(RegistryKey<World> worldKey, RegionPos rPos, BitSet bitSet) {
		if (!personal() && Surveyor.CONFIG.networking.terrain.atLeast(NetworkMode.SERVER)) return bitSet;
		if (terrain().contains(worldKey, rPos)) {
			bitSet.and(terrain().get(worldKey, rPos));
		} else {
			bitSet.clear();
		}
		return bitSet;
	}

	default Map<RegionPos, BitSet> limitTerrainBitset(RegistryKey<World> worldKey, Map<RegionPos, BitSet> bitSets) {
		if (!personal() && Surveyor.CONFIG.networking.terrain.atLeast(NetworkMode.SERVER)) return bitSets;
		if (terrain().row(worldKey).isEmpty()) {
			bitSets.clear();
		} else {
			bitSets.forEach((rPos, set) -> limitTerrainBitset(worldKey, rPos, set));
		}
		return bitSets;
	}

	default Multimap<RegistryKey<Structure>, ChunkPos> limitStructureKeySet(RegistryKey<World> worldKey, Multimap<RegistryKey<Structure>, ChunkPos> keySet) {
		if (!personal() && Surveyor.CONFIG.networking.structures.atLeast(NetworkMode.SERVER)) return keySet;
		if (structures().row(worldKey).isEmpty()) {
			keySet.clear();
		} else {
			keySet.keySet().removeIf(key -> !structures().contains(worldKey, key));
			keySet.entries().removeIf(e -> !structures().get(worldKey, keySet).contains(e.getValue().toLong()));
		}
		return keySet;
	}

	default Table<UUID, Identifier, Landmark> limitLandmarkMap(RegistryKey<World> worldKey, Table<UUID, Identifier, Landmark> asMap) {
		Table<UUID, Identifier, Landmark> outTable = HashBasedTable.create(asMap);
		outTable.values().removeIf(l -> !exploredLandmark(worldKey, l));
		return outTable;
	}

	default Multimap<UUID, Identifier> limitLandmarkKeySet(RegistryKey<World> worldKey, WorldLandmarks worldLandmarks, Multimap<UUID, Identifier> keySet) {
		Multimap<UUID, Identifier> toRemove = HashMultimap.create();
		keySet.forEach((uuid, id) -> {
			if (!worldLandmarks.contains(uuid, id) || !exploredLandmark(worldKey, worldLandmarks.get(uuid, id))) toRemove.put(uuid, id);
		});
		toRemove.forEach(keySet::remove);
		return keySet;
	}

	default void updateClientForMergeRegion(World world, RegionPos regionPos, BitSet bitSet) {
		Set<ChunkPos> terrainKeys = bitSet.stream().mapToObj(regionPos::toChunk).collect(Collectors.toSet());
		SurveyorClientEvents.Invoke.terrainUpdated(world, terrainKeys);
		Multimap<UUID, Identifier> landmarkKeys = HashMultimap.create();
		WorldLandmarks summary = WorldSummary.of(world).landmarks();
		if (summary == null) return;
		summary.asMap(this).values().forEach(landmark -> {
			if (landmark.components().contains(LandmarkComponentTypes.POS) && terrainKeys.contains(new ChunkPos(landmark.components().get(LandmarkComponentTypes.POS))) && landmark.owner().equals(WorldLandmarks.GLOBAL)) landmarkKeys.put(landmark.owner(), landmark.id());
		});
		SurveyorClientEvents.Invoke.landmarksAdded(world, landmarkKeys);
	}

	default void updateClientForLandmarks(WorldSummary worldSummary) {
		WorldLandmarks summary = worldSummary.landmarks();
		if (summary == null) return;
		Multimap<UUID, Identifier> unexploredLandmarks = summary.keySet(null);
		Multimap<UUID, Identifier> exploredLandmarks = summary.keySet(this);
		exploredLandmarks.forEach(unexploredLandmarks::remove);
		SurveyorClientEvents.Invoke.landmarksAdded(worldSummary, exploredLandmarks);
		SurveyorClientEvents.Invoke.landmarksRemoved(worldSummary, unexploredLandmarks);
	}

	default void mergeRegion(RegistryKey<World> worldKey, RegionPos regionPos, BitSet bitSet) {
		if (!terrain().contains(worldKey, regionPos)) terrain().put(worldKey, regionPos, new BitSet(RegionPos.CHUNK_SIZE));
		terrain().get(worldKey, regionPos).or(bitSet);
	}

	default void replaceTerrain(Table<RegistryKey<World>, RegionPos, BitSet> terrain) {
		terrain().putAll(terrain);
	}

	default void updateClientForAddChunk(World world, ChunkPos chunkPos) {
		SurveyorClientEvents.Invoke.terrainUpdated(world, chunkPos);
		Multimap<UUID, Identifier> landmarkKeys = HashMultimap.create();
		WorldLandmarks summary = WorldSummary.of(world).landmarks();
		if (summary == null) return;
		summary.asMap(this).values().forEach(landmark -> {
			if (landmark.components().contains(LandmarkComponentTypes.POS) && chunkPos.equals(new ChunkPos(landmark.components().get(LandmarkComponentTypes.POS))) && landmark.owner().equals(WorldLandmarks.GLOBAL)) landmarkKeys.put(landmark.owner(), landmark.id());
		});
		SurveyorClientEvents.Invoke.landmarksAdded(world, landmarkKeys);
	}

	default void addChunk(RegistryKey<World> worldKey, ChunkPos pos) {
		RegionPos regionPos = RegionPos.of(pos);
		if (!terrain().contains(worldKey, regionPos)) terrain().put(worldKey, regionPos, new BitSet(RegionPos.CHUNK_SIZE));
		terrain().get(worldKey, regionPos).set(RegionPos.chunkToBit(pos));
	}

	default void updateClientForAddStructure(World world, RegistryKey<Structure> structureKey, ChunkPos pos) {
		SurveyorClientEvents.Invoke.structuresAdded(world, structureKey, pos);
	}

	default void addStructure(RegistryKey<World> worldKey, RegistryKey<Structure> structureKey, ChunkPos pos) {
		if (!structures().contains(worldKey, structureKey)) structures().put(worldKey, structureKey, new LongOpenHashSet());
		structures().get(worldKey, structureKey).add(pos.toLong());
	}

	default void mergeStructures(RegistryKey<World> worldKey, RegistryKey<Structure> structureKey, LongSet starts) {
		if (!structures().contains(worldKey, structureKey)) structures().put(worldKey, structureKey, new LongOpenHashSet());
		structures().get(worldKey, structureKey).addAll(starts);
	}

	default void replaceStructures(Table<RegistryKey<World>, RegistryKey<Structure>, LongSet> structures) {
		structures().putAll(structures);
	}

	default NbtCompound write(NbtCompound nbt) {
		NbtCompound terrainCompound = new NbtCompound();
		terrain().rowMap().forEach((worldKey, map) -> {
			LongList regionLongs = new LongArrayList();
			for (Map.Entry<RegionPos, BitSet> entry : map.entrySet()) {
				regionLongs.add(entry.getKey().toLong());
				if (entry.getValue().cardinality() == RegionPos.CHUNK_AREA) {
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
		structures().rowMap().forEach((worldKey, map) -> {
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
			Map<RegionPos, BitSet> regionMap = new HashMap<>();
			for (int i = 0; i + 1 < regionArray.length; i += 2) {
				RegionPos rPos = RegionPos.of(regionArray[i]);
				int bitLength = (int) regionArray[i + 1];
				if (bitLength == -1) {
					BitSet set = new BitSet(RegionPos.CHUNK_AREA);
					set.set(0, RegionPos.CHUNK_AREA);
					regionMap.put(rPos, set);
				} else {
					long[] bitArray = new long[bitLength];
					System.arraycopy(regionArray, i + 2, bitArray, 0, bitLength);
					regionMap.put(rPos, BitSet.valueOf(bitArray));
					i += bitLength;
				}
			}
			terrain().rowMap().put(RegistryKey.of(RegistryKeys.WORLD, new Identifier(worldKeyString)), regionMap);
		}

		NbtCompound structuresCompound = nbt.getCompound(KEY_EXPLORED_STRUCTURES);
		for (String worldKeyString : structuresCompound.getKeys()) {
			Map<RegistryKey<Structure>, LongSet> structureMap = new HashMap<>();
			NbtCompound worldStructuresCompound = structuresCompound.getCompound(worldKeyString);
			for (String key : worldStructuresCompound.getKeys()) {
				structureMap.put(RegistryKey.of(RegistryKeys.STRUCTURE, new Identifier(key)), new LongOpenHashSet(worldStructuresCompound.getLongArray(key)));
			}
			structures().rowMap().put(RegistryKey.of(RegistryKeys.WORLD, new Identifier(worldKeyString)), structureMap);
		}
	}
}
