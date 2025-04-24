package folk.sisby.surveyor.structure;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.SurveyorEvents;
import folk.sisby.surveyor.SurveyorExploration;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.config.SystemMode;
import folk.sisby.surveyor.packet.S2CStructuresAddedPacket;
import folk.sisby.surveyor.terrain.RegionSummary;
import folk.sisby.surveyor.util.ChunkUtil;
import folk.sisby.surveyor.util.MapUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.nbt.NbtCrashException;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class WorldStructureSummary {
	public static final String KEY_STRUCTURES = "structures";
	public static final String KEY_TYPE = "type";
	public static final String KEY_TAGS = "tags";

	protected final RegistryKey<World> worldKey;
	protected final Map<ChunkPos, RegionStructureSummary> regions = new ConcurrentHashMap<>();
	protected final Map<RegistryKey<Structure>, RegistryKey<StructureType<?>>> structureTypes = new ConcurrentHashMap<>();
	protected final Multimap<RegistryKey<Structure>, TagKey<Structure>> structureTags = Multimaps.synchronizedSetMultimap(HashMultimap.create());
	protected boolean dirty = false;

	public WorldStructureSummary(RegistryKey<World> worldKey, Map<ChunkPos, RegionStructureSummary> regions, Map<RegistryKey<Structure>, RegistryKey<StructureType<?>>> structureTypes, Multimap<RegistryKey<Structure>, TagKey<Structure>> structureTags) {
		this.worldKey = worldKey;
		this.regions.putAll(regions);
		this.structureTypes.putAll(structureTypes);
		this.structureTags.putAll(structureTags);
	}

	protected static ChunkPos regionPosOf(ChunkPos pos) {
		return new ChunkPos(pos.x >> RegionSummary.REGION_POWER, pos.z >> RegionSummary.REGION_POWER);
	}

	public static StructurePieceSummary readStructurePieceNbt(NbtCompound nbt) {
		if (nbt.getString("id").equals(Registries.STRUCTURE_PIECE.getId(StructurePieceType.JIGSAW).toString())) {
			return new JigsawPieceSummary(nbt);
		} else {
			return new StructurePieceSummary(nbt);
		}
	}

	protected static WorldStructureSummary readNbt(RegistryKey<World> worldKey, NbtCompound nbt, Map<ChunkPos, RegionStructureSummary> regions) {
		Map<RegistryKey<Structure>, RegistryKey<StructureType<?>>> structureTypes = new ConcurrentHashMap<>();
		Multimap<RegistryKey<Structure>, TagKey<Structure>> structureTags = HashMultimap.create();
		NbtCompound structuresCompound = nbt.getCompound(KEY_STRUCTURES);
		for (String structureId : structuresCompound.getKeys()) {
			RegistryKey<Structure> key = RegistryKey.of(RegistryKeys.STRUCTURE, Identifier.of(structureId));
			NbtCompound structureCompound = structuresCompound.getCompound(structureId);
			RegistryKey<StructureType<?>> type = RegistryKey.of(RegistryKeys.STRUCTURE_TYPE, Identifier.of(structureCompound.getString(KEY_TYPE)));
			structureTypes.put(key, type);
			Collection<TagKey<Structure>> tags = structureCompound.getList(KEY_TAGS, NbtElement.STRING_TYPE).stream().map(e -> TagKey.of(RegistryKeys.STRUCTURE, Identifier.of(e.asString()))).toList();
			structureTags.putAll(key, tags);
		}
		for (RegionStructureSummary region : regions.values()) {
			region.structures.keySet().removeIf(k -> !structureTypes.containsKey(k));
		}
		return new WorldStructureSummary(worldKey, regions, structureTypes, structureTags);
	}

	public static WorldStructureSummary load(World world, File folder) {
		File structuresFile = new File(folder, "structures.dat");
		NbtCompound worldNbt = new NbtCompound();
		if (structuresFile.exists()) {
			try {
				worldNbt = NbtIo.readCompressed(structuresFile.toPath(), NbtSizeTracker.ofUnlimitedBytes());
			} catch (IOException | NbtCrashException e) {
				Surveyor.LOGGER.error("[Surveyor] Error loading structure summary file for {}.", world.getRegistryKey().getValue(), e);
			}
		}
		Map<ChunkPos, RegionStructureSummary> regions = new HashMap<>();
		ChunkUtil.getRegionNbt(folder, "s").forEach((pos, nbt) -> regions.put(pos, RegionStructureSummary.readNbt(nbt)));
		if (regions.isEmpty()) { // Try load legacy data
			RegionStructureSummary worldSummary = RegionStructureSummary.readNbt(worldNbt);
			worldSummary.structures.forEach((key, map) -> map.forEach((pos, start) -> {
				ChunkPos rPos = regionPosOf(pos);
				regions.computeIfAbsent(rPos, k -> new RegionStructureSummary()).put(key, pos, start);
			}));
		}
		return readNbt(world.getRegistryKey(), worldNbt, regions);
	}

	public static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
		WorldStructureSummary structures = WorldSummary.of(world).structures();
		chunk.getStructureStarts().forEach((structure, start) -> {
			if (structures != null && !structures.contains(world, start)) structures.put(world, start);
		});
	}

	public static void onStructurePlace(ServerWorld world, StructureStart start) {
		WorldStructureSummary structures = WorldSummary.of(world).structures();
		if (structures != null && !structures.contains(world, start)) structures.put(world, start);
	}

	public RegistryKey<StructureType<?>> getType(RegistryKey<Structure> key) {
		return structureTypes.get(key);
	}

	public Collection<TagKey<Structure>> getTags(RegistryKey<Structure> key) {
		return structureTags.get(key);
	}

	public boolean contains(World world, StructureStart start) {
		ChunkPos rPos = regionPosOf(start.getPos());
		return regions.containsKey(rPos) && regions.get(rPos).contains(world, start);
	}

	public boolean contains(RegistryKey<Structure> key, ChunkPos pos) {
		ChunkPos rPos = regionPosOf(pos);
		return regions.containsKey(rPos) && regions.get(rPos).contains(key, pos);
	}

	public StructureStartSummary get(RegistryKey<Structure> key, ChunkPos pos) {
		ChunkPos rPos = regionPosOf(pos);
		return regions.containsKey(rPos) ? regions.get(rPos).get(key, pos) : null;
	}

	public Map<RegistryKey<Structure>, Map<ChunkPos, StructureStartSummary>> asMap(SurveyorExploration exploration) {
		Multimap<RegistryKey<Structure>, ChunkPos> keySet = keySet(exploration);
		Map<RegistryKey<Structure>, Map<ChunkPos, StructureStartSummary>> map = new HashMap<>();
		keySet.forEach((key, pos) -> map.computeIfAbsent(key, k -> new HashMap<>()).put(pos, get(key, pos)));
		return map;
	}

	public Multimap<RegistryKey<Structure>, ChunkPos> keySet(SurveyorExploration exploration) {
		Multimap<RegistryKey<Structure>, ChunkPos> map = HashMultimap.create();
		regions.values().forEach(r -> map.putAll(r.keySet()));
		if (exploration != null) exploration.limitStructureKeySet(worldKey, map);
		return map;
	}

	public void put(ServerWorld world, StructureStart start) {
		if (Surveyor.CONFIG.structures == SystemMode.FROZEN) return;
		ChunkPos rPos = regionPosOf(start.getPos());
		RegistryKey<Structure> key = world.getRegistryManager().get(RegistryKeys.STRUCTURE).getKey(start.getStructure()).orElseThrow();
		Optional<RegistryKey<StructureType<?>>> type = world.getRegistryManager().get(RegistryKeys.STRUCTURE_TYPE).getKey(start.getStructure().getType());
		if (!start.hasChildren()) {
			Surveyor.LOGGER.error("Cowardly refusing to save structure {} as it has no pieces! Report this to the structure mod author!", key.getValue());
			return;
		}
		if (type.isEmpty()) {
			Surveyor.LOGGER.error("Cowardly refusing to save structure {} as it has no structure type! Report this to the structure mod author!", key.getValue());
			return;
		}
		regions.computeIfAbsent(rPos, k -> new RegionStructureSummary()).put(world, start);
		List<TagKey<Structure>> tags = world.getRegistryManager().get(RegistryKeys.STRUCTURE).getEntry(start.getStructure()).streamTags().toList();
		structureTypes.put(key, type.orElseThrow());
		structureTags.putAll(key, tags);
		dirty();
		SurveyorEvents.Invoke.structuresAdded(world, key, start.getPos());
	}

	public void put(World world, RegistryKey<Structure> key, ChunkPos pos, StructureStartSummary summary, RegistryKey<StructureType<?>> type, Collection<TagKey<Structure>> tagKeys) {
		if (Surveyor.CONFIG.structures == SystemMode.FROZEN) return;
		ChunkPos rPos = regionPosOf(pos);
		regions.computeIfAbsent(rPos, k -> new RegionStructureSummary()).put(key, pos, summary);
		structureTypes.put(key, type);
		structureTags.putAll(key, tagKeys);
		dirty();
		SurveyorEvents.Invoke.structuresAdded(world, key, pos);
	}

	protected NbtCompound writeNbt(NbtCompound nbt) {
		NbtCompound structuresCompound = new NbtCompound();
		structureTypes.forEach((key, starts) -> {
			NbtCompound structureCompound = new NbtCompound();
			structureCompound.putString(KEY_TYPE, structureTypes.get(key).getValue().toString());
			structureCompound.put(KEY_TAGS, new NbtList(structureTags.get(key).stream().map(t -> (NbtElement) NbtString.of(t.id().toString())).toList(), NbtElement.STRING_TYPE));
			structuresCompound.put(key.getValue().toString(), structureCompound);
		});
		nbt.put(KEY_STRUCTURES, structuresCompound);
		return nbt;
	}

	public int save(World world, File folder) {
		List<ChunkPos> savedRegions = new ArrayList<>();
		if (isDirty()) {
			File structureFile = new File(folder, "structures.dat");
			NbtCompound structureCompound = writeNbt(new NbtCompound());
			Util.getIoWorkerExecutor().execute(() -> {
					try {
						NbtIo.writeCompressed(structureCompound, structureFile.toPath());
					} catch (IOException e) {
						Surveyor.LOGGER.error("[Surveyor] Error writing world structure summary file for {}.", world.getRegistryKey().getValue(), e);
					}
			});
			dirty = false;
			regions.forEach((pos, summary) -> {
				if (!summary.isDirty()) return;
				savedRegions.add(pos);
				NbtCompound regionCompound = summary.writeNbt(new NbtCompound());
				File regionFile = new File(folder, "s.%d.%d.dat".formatted(pos.x, pos.z));
				Util.getIoWorkerExecutor().execute(() -> {
					try {
						NbtIo.writeCompressed(regionCompound, regionFile.toPath());
					} catch (IOException e) {
						Surveyor.LOGGER.error("[Surveyor] Error writing region structure summary file {}.", regionFile.getName(), e);
					}
				});
				summary.dirty = false;
			});
		}
		return savedRegions.size();
	}

	public Multimap<RegistryKey<Structure>, ChunkPos> readUpdatePacket(World world, S2CStructuresAddedPacket packet) {
		if (Surveyor.CONFIG.structures == SystemMode.FROZEN) return HashMultimap.create();
		packet.structures().forEach((key, map) -> map.forEach((pos, start) -> put(world, key, pos, start, packet.types().get(key), packet.tags().get(key))));
		return MapUtil.keyMultiMap(packet.structures());
	}

	public S2CStructuresAddedPacket createUpdatePacket(boolean shared, Multimap<RegistryKey<Structure>, ChunkPos> keySet) {
		Map<RegistryKey<Structure>, Map<ChunkPos, StructureStartSummary>> packetStructures = new HashMap<>();
		Map<RegistryKey<Structure>, RegistryKey<StructureType<?>>> packetTypes = new HashMap<>();
		Multimap<RegistryKey<Structure>, TagKey<Structure>> packetTags = HashMultimap.create();
		keySet.forEach((key, pos) -> packetStructures.computeIfAbsent(key, k -> new HashMap<>()).put(pos, get(key, pos)));
		for (RegistryKey<Structure> key : keySet.keySet()) {
			packetTypes.put(key, getType(key));
			packetTags.putAll(key, getTags(key));
		}
		return new S2CStructuresAddedPacket(shared, packetStructures, packetTypes, packetTags);
	}

	public boolean isDirty() {
		return (dirty || regions.values().stream().anyMatch(RegionStructureSummary::isDirty)) && Surveyor.CONFIG.structures != SystemMode.FROZEN;
	}

	private void dirty() {
		dirty = true;
	}
}
