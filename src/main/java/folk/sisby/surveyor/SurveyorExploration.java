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

import java.util.BitSet;
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
		return ServerSummary.of(server).getExploration(player);
	}

	static SurveyorExploration ofShared(ServerPlayerEntity player) {
		return ofShared(Surveyor.getUuid(player), player.getServer());
	}

	static SurveyorExploration ofShared(UUID player, MinecraftServer server) {
		return ServerSummary.of(server).getSharingExploration(player, NetworkMode.GROUP, true);
	}

	Table<RegistryKey<World>, RegionPos, BitSet> chunks();

	Table<RegistryKey<World>, RegistryKey<Structure>, LongSet> starts();

	Set<UUID> sharedPlayers();

	default void copyFrom(SurveyorExploration oldExploration) {
		chunks().putAll(oldExploration.chunks());
		starts().putAll(oldExploration.starts());
	}

	boolean personal();

	default boolean exploredChunk(RegistryKey<World> dimension, ChunkPos pos) {
		RegionPos regionPos = RegionPos.of(pos);
		return !personal() && Surveyor.CONFIG.networking.terrain.atLeast(NetworkMode.SERVER) || chunks().contains(dimension, regionPos) && chunks().get(dimension, regionPos).get(RegionPos.chunkToBit(pos));
	}

	default boolean exploredStructure(RegistryKey<World> dimension, RegistryKey<Structure> structure, ChunkPos pos) {
		return !personal() && Surveyor.CONFIG.networking.structures.atLeast(NetworkMode.SERVER) || starts().contains(dimension, structure) && starts().get(dimension, structure).contains(pos.toLong());
	}

	default boolean exploredLandmark(RegistryKey<World> dimension, Landmark landmark) {
		return landmark.owner().equals(WorldLandmarks.GLOBAL) ? !landmark.components().contains(LandmarkComponentTypes.POS) || exploredChunk(dimension, new ChunkPos(landmark.components().get(LandmarkComponentTypes.POS))) : sharedPlayers().contains(landmark.owner());
	}

	default int chunkCount() {
		return chunks().values().stream().mapToInt(BitSet::cardinality).sum();
	}

	default int structureCount() {
		return starts().values().stream().mapToInt(LongSet::size).sum();
	}

	default BitSet limit(RegistryKey<World> dimension, RegionPos regionPos, BitSet chunks) {
		if (!personal() && Surveyor.CONFIG.networking.terrain.atLeast(NetworkMode.SERVER)) return chunks;
		if (chunks().contains(dimension, regionPos)) {
			chunks.and(chunks().get(dimension, regionPos));
		} else {
			chunks.clear();
		}
		return chunks;
	}

	default Map<RegionPos, BitSet> limit(RegistryKey<World> dimension, Map<RegionPos, BitSet> chunks) {
		if (!personal() && Surveyor.CONFIG.networking.terrain.atLeast(NetworkMode.SERVER)) return chunks;
		if (chunks().row(dimension).isEmpty()) {
			chunks.clear();
		} else {
			chunks.forEach((regionPos, set) -> limit(dimension, regionPos, set));
		}
		return chunks;
	}

	default Multimap<RegistryKey<Structure>, ChunkPos> limit(RegistryKey<World> dimension, Multimap<RegistryKey<Structure>, ChunkPos> starts) {
		if (!personal() && Surveyor.CONFIG.networking.structures.atLeast(NetworkMode.SERVER)) return starts;
		if (starts().row(dimension).isEmpty()) {
			starts.clear();
		} else {
			starts.keySet().removeIf(key -> !starts().contains(dimension, key));
			starts.entries().removeIf(e -> !starts().get(dimension, e.getKey()).contains(e.getValue().toLong()));
		}
		return starts;
	}

	default Table<UUID, Identifier, Landmark> limit(RegistryKey<World> dimension, Table<UUID, Identifier, Landmark> landmarks) {
		Table<UUID, Identifier, Landmark> outTable = HashBasedTable.create(landmarks);
		outTable.values().removeIf(l -> !exploredLandmark(dimension, l));
		return outTable;
	}

	default Multimap<UUID, Identifier> limit(RegistryKey<World> dimension, WorldLandmarks landmarks, Multimap<UUID, Identifier> keys) {
		Multimap<UUID, Identifier> toRemove = HashMultimap.create();
		keys.forEach((uuid, id) -> {
			if (!landmarks.contains(uuid, id) || !exploredLandmark(dimension, landmarks.get(uuid, id))) toRemove.put(uuid, id);
		});
		toRemove.forEach(keys::remove);
		return keys;
	}

	default void updateClientForMergeRegion(WorldSummary summary, RegionPos regionPos, BitSet chunks) {
		SurveyorClientEvents.Invoke.terrainUpdated(summary, Map.of(regionPos, chunks));
		Multimap<UUID, Identifier> landmarkKeys = HashMultimap.create();
		WorldLandmarks landmarks = summary == null ? null : summary.landmarks();
		if (landmarks == null) return;
		Set<ChunkPos> terrainKeys = chunks.stream().mapToObj(regionPos::toChunk).collect(Collectors.toSet());
		landmarks.asMap(this).values().forEach(landmark -> {
			if (landmark.components().contains(LandmarkComponentTypes.POS) && terrainKeys.contains(new ChunkPos(landmark.components().get(LandmarkComponentTypes.POS))) && landmark.owner().equals(WorldLandmarks.GLOBAL)) landmarkKeys.put(landmark.owner(), landmark.id());
		});
		SurveyorClientEvents.Invoke.landmarksAdded(summary, landmarkKeys);
	}

	default void updateClientForLandmarks(WorldSummary summary) {
		WorldLandmarks landmarks = summary.landmarks();
		if (landmarks == null) return;
		Multimap<UUID, Identifier> unexploredLandmarks = landmarks.keySet(null);
		Multimap<UUID, Identifier> exploredLandmarks = landmarks.keySet(this);
		exploredLandmarks.forEach(unexploredLandmarks::remove);
		SurveyorClientEvents.Invoke.landmarksAdded(summary, exploredLandmarks);
		SurveyorClientEvents.Invoke.landmarksRemoved(summary, unexploredLandmarks);
	}

	default void mergeRegion(RegistryKey<World> dimension, RegionPos regionPos, BitSet chunks, boolean updateClient) {
		if (!chunks().contains(dimension, regionPos)) chunks().put(dimension, regionPos, new BitSet(RegionPos.CHUNK_AREA));
		chunks().get(dimension, regionPos).or(chunks);
	}

	default void replaceTerrain(Table<RegistryKey<World>, RegionPos, BitSet> chunks, boolean updateClient) {
		for (RegistryKey<World> dimension : chunks.rowKeySet()) { // merge first for client updates
			chunks.row(dimension).forEach((regionPos, bitSet) -> mergeRegion(dimension, regionPos, bitSet, updateClient));
		}
		chunks().putAll(chunks); // then replace to ditch anything else
	}

	default void updateClientForAddChunk(WorldSummary summary, ChunkPos chunkPos) {
		SurveyorClientEvents.Invoke.terrainUpdated(summary, chunkPos);
		Multimap<UUID, Identifier> landmarkKeys = HashMultimap.create();
		WorldLandmarks landmarks = summary == null ? null : summary.landmarks();
		if (landmarks == null) return;
		landmarks.asMap(this).values().forEach(landmark -> {
			if (landmark.components().contains(LandmarkComponentTypes.POS) && chunkPos.equals(new ChunkPos(landmark.components().get(LandmarkComponentTypes.POS))) && landmark.owner().equals(WorldLandmarks.GLOBAL)) landmarkKeys.put(landmark.owner(), landmark.id());
		});
		SurveyorClientEvents.Invoke.landmarksAdded(summary, landmarkKeys);
	}

	default void addChunk(RegistryKey<World> dimension, ChunkPos pos, boolean updateClient) {
		RegionPos regionPos = RegionPos.of(pos);
		if (!chunks().contains(dimension, regionPos)) chunks().put(dimension, regionPos, new BitSet(RegionPos.CHUNK_AREA));
		chunks().get(dimension, regionPos).set(RegionPos.chunkToBit(pos));
	}

	default void updateClientForAddStructure(WorldSummary summary, RegistryKey<Structure> structureKey, ChunkPos pos) {
		SurveyorClientEvents.Invoke.structuresAdded(summary, structureKey, pos);
	}

	default void addStructure(RegistryKey<World> dimension, RegistryKey<Structure> structureKey, ChunkPos pos) {
		if (!starts().contains(dimension, structureKey)) starts().put(dimension, structureKey, new LongOpenHashSet());
		starts().get(dimension, structureKey).add(pos.toLong());
	}

	default void mergeStructures(RegistryKey<World> dimension, RegistryKey<Structure> structureKey, LongSet starts) {
		if (!starts().contains(dimension, structureKey)) starts().put(dimension, structureKey, new LongOpenHashSet());
		starts().get(dimension, structureKey).addAll(starts);
	}

	default void replaceStructures(Table<RegistryKey<World>, RegistryKey<Structure>, LongSet> starts) {
		for (RegistryKey<World> dimension : starts.rowKeySet()) { // merge first for client updates
			starts.row(dimension).forEach((key, startSet) -> mergeStructures(dimension, key, startSet));
		}
		starts.putAll(starts); // then replace to ditch anything else
	}

	default NbtCompound write(NbtCompound nbt) {
		NbtCompound terrainCompound = new NbtCompound();
		chunks().rowMap().forEach((dimension, map) -> {
			LongList regionLongs = new LongArrayList();
			for (Map.Entry<RegionPos, BitSet> entry : map.entrySet()) {
				regionLongs.add(entry.getKey().toLong());
				if (entry.getValue().cardinality() == RegionPos.CHUNK_AREA) {
					regionLongs.add(-1);
				} else {
					long[] chunks = entry.getValue().toLongArray();
					regionLongs.add(chunks.length);
					regionLongs.addAll(LongList.of(chunks));
				}
			}
			terrainCompound.putLongArray(dimension.getValue().toString(), regionLongs.toLongArray());
		});
		nbt.put(KEY_EXPLORED_TERRAIN, terrainCompound);

		NbtCompound structuresCompound = new NbtCompound();
		starts().rowMap().forEach((dimension, map) -> {
			NbtCompound worldStructuresCompound = new NbtCompound();
			for (RegistryKey<Structure> structure : map.keySet()) {
				worldStructuresCompound.putLongArray(structure.getValue().toString(), map.get(structure).toLongArray());
			}
			structuresCompound.put(dimension.getValue().toString(), worldStructuresCompound);
		});
		nbt.put(KEY_EXPLORED_STRUCTURES, structuresCompound);
		return nbt;
	}

	default void read(NbtCompound nbt) {
		NbtCompound terrainCompound = nbt.getCompound(KEY_EXPLORED_TERRAIN);
		chunks().clear();
		starts().clear();
		for (String dimensionString : terrainCompound.getKeys()) {
			RegistryKey<World> dimension = RegistryKey.of(RegistryKeys.WORLD, Identifier.tryParse(dimensionString));
			long[] regionArray = terrainCompound.getLongArray(dimensionString);
			for (int i = 0; i + 1 < regionArray.length; i += 2) {
				RegionPos regionPos = RegionPos.of(regionArray[i]);
				int bitLength = (int) regionArray[i + 1];
				if (bitLength == -1) {
					BitSet set = new BitSet(RegionPos.CHUNK_AREA);
					set.set(0, RegionPos.CHUNK_AREA);
					chunks().put(dimension, regionPos, set);
				} else {
					long[] bitArray = new long[bitLength];
					System.arraycopy(regionArray, i + 2, bitArray, 0, bitLength);
					chunks().put(dimension, regionPos, BitSet.valueOf(bitArray));
					i += bitLength;
				}
			}
		}

		NbtCompound structuresCompound = nbt.getCompound(KEY_EXPLORED_STRUCTURES);
		for (String dimensionString : structuresCompound.getKeys()) {
			RegistryKey<World> dimension = RegistryKey.of(RegistryKeys.WORLD, Identifier.tryParse(dimensionString));
			NbtCompound worldStructuresCompound = structuresCompound.getCompound(dimensionString);
			for (String key : worldStructuresCompound.getKeys()) {
				starts().put(dimension, RegistryKey.of(RegistryKeys.STRUCTURE, Identifier.tryParse(key)), new LongOpenHashSet(worldStructuresCompound.getLongArray(key)));
			}
		}
	}
}
