package folk.sisby.surveyor.structure;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Table;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.SurveyorEvents;
import folk.sisby.surveyor.SurveyorExploration;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.config.SystemMode;
import folk.sisby.surveyor.packet.S2CStructuresAddedPacket;
import folk.sisby.surveyor.util.ChunkUtil;
import folk.sisby.surveyor.util.MapUtil;
import folk.sisby.surveyor.util.RegionPos;
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

public class WorldStructures {
	public static final String KEY_STRUCTURES = "structures";
	public static final String KEY_TYPE = "type";
	public static final String KEY_TAGS = "tags";

	protected final WorldSummary summary;
	protected final Map<RegionPos, RegionStructureSummary> regions = new ConcurrentHashMap<>();
	protected final Map<RegistryKey<Structure>, RegistryKey<StructureType<?>>> structureTypes = new ConcurrentHashMap<>();
	protected final Multimap<RegistryKey<Structure>, TagKey<Structure>> structureTags = Multimaps.synchronizedSetMultimap(HashMultimap.create());
	protected boolean dirty = false;

	public static WorldStructures of(World world) {
		return Optional.ofNullable(world).map(WorldSummary::of).map(WorldSummary::structures).orElse(null);
	}

	public WorldStructures(WorldSummary summary, Map<RegionPos, RegionStructureSummary> regions, Map<RegistryKey<Structure>, RegistryKey<StructureType<?>>> structureTypes, Multimap<RegistryKey<Structure>, TagKey<Structure>> structureTags) {
		this.summary = summary;
		this.regions.putAll(regions);
		this.structureTypes.putAll(structureTypes);
		this.structureTags.putAll(structureTags);
	}

	public static StructurePieceSummary readStructurePieceNbt(NbtCompound nbt) {
		if (nbt.getString("id").equals(Registries.STRUCTURE_PIECE.getId(StructurePieceType.JIGSAW).toString())) {
			return new JigsawPieceSummary(nbt);
		} else {
			return new StructurePieceSummary(nbt);
		}
	}

	protected static WorldStructures readNbt(WorldSummary summary, NbtCompound nbt, Map<RegionPos, RegionStructureSummary> regions) {
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
			region.starts.rowMap().keySet().removeIf(k -> !structureTypes.containsKey(k));
		}
		return new WorldStructures(summary, regions, structureTypes, structureTags);
	}

	public static WorldStructures load(WorldSummary summary, File folder) {
		File structuresFile = new File(folder, "structures.dat");
		NbtCompound worldNbt = new NbtCompound();
		if (structuresFile.exists()) {
			try {
				worldNbt = NbtIo.readCompressed(structuresFile.toPath(), NbtSizeTracker.ofUnlimitedBytes());
			} catch (IOException | NbtCrashException e) {
				Surveyor.LOGGER.error("[Surveyor] Error loading structure summary file for {}.", summary.dimension().getValue(), e);
			}
		}
		Map<RegionPos, RegionStructureSummary> regions = new HashMap<>();
		ChunkUtil.getRegionNbt(folder, "s").forEach((pos, nbt) -> regions.put(pos, RegionStructureSummary.readNbt(nbt)));
		return readNbt(summary, worldNbt, regions);
	}

	public static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
		WorldStructures structures = WorldStructures.of(world);
		if (structures == null) return;
		chunk.getStructureStarts().forEach((structure, start) -> {
			if (!structures.contains(world, start)) structures.put(world, start);
		});
	}

	public static void onStructurePlace(ServerWorld world, StructureStart start) {
		WorldStructures structures = WorldStructures.of(world);
		if (structures != null && !structures.contains(world, start)) structures.put(world, start);
	}

	public RegistryKey<StructureType<?>> getType(RegistryKey<Structure> key) {
		return structureTypes.get(key);
	}

	public Collection<TagKey<Structure>> getTags(RegistryKey<Structure> key) {
		return structureTags.get(key);
	}

	public boolean contains(World world, StructureStart start) {
		RegionPos regionPos = RegionPos.of(start.getPos());
		return regions.containsKey(regionPos) && regions.get(regionPos).contains(world, start);
	}

	public boolean contains(RegistryKey<Structure> key, ChunkPos pos) {
		RegionPos regionPos = RegionPos.of(pos);
		return regions.containsKey(regionPos) && regions.get(regionPos).contains(key, pos);
	}

	public StructureStartSummary get(RegistryKey<Structure> key, ChunkPos pos) {
		RegionPos regionPos = RegionPos.of(pos);
		return regions.containsKey(regionPos) ? regions.get(regionPos).get(key, pos) : null;
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
		if (exploration != null) exploration.limit(summary.dimension(), map);
		return map;
	}

	public void put(ServerWorld world, StructureStart start) {
		if (Surveyor.CONFIG.structures == SystemMode.FROZEN) return;
		RegionPos regionPos = RegionPos.of(start.getPos());
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
		regions.computeIfAbsent(regionPos, k -> new RegionStructureSummary()).put(world, start);
		List<TagKey<Structure>> tags = world.getRegistryManager().get(RegistryKeys.STRUCTURE).getEntry(start.getStructure()).streamTags().toList();
		structureTypes.put(key, type.orElseThrow());
		structureTags.putAll(key, tags);
		dirty();
		SurveyorEvents.Invoke.structuresAdded(summary, key, start.getPos());
	}

	public void put(RegistryKey<Structure> key, ChunkPos pos, StructureStartSummary start, RegistryKey<StructureType<?>> type, Collection<TagKey<Structure>> tagKeys) {
		if (Surveyor.CONFIG.structures == SystemMode.FROZEN) return;
		RegionPos regionPos = RegionPos.of(pos);
		regions.computeIfAbsent(regionPos, k -> new RegionStructureSummary()).put(key, pos, start);
		structureTypes.put(key, type);
		structureTags.putAll(key, tagKeys);
		dirty();
		SurveyorEvents.Invoke.structuresAdded(summary, key, pos);
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

	public int save(File folder) {
		List<RegionPos> savedRegions = new ArrayList<>();
		if (isDirty()) {
			File structureFile = new File(folder, "structures.dat");
			NbtCompound structureCompound = writeNbt(new NbtCompound());
			Util.getIoWorkerExecutor().execute(() -> {
					try {
						NbtIo.writeCompressed(structureCompound, structureFile.toPath());
					} catch (IOException e) {
						Surveyor.LOGGER.error("[Surveyor] Error writing world structure summary file for {}.", summary.dimension().getValue(), e);
					}
			});
			dirty = false;
			regions.forEach((pos, summary) -> {
				if (!summary.isDirty()) return;
				savedRegions.add(pos);
				NbtCompound regionCompound = summary.writeNbt(new NbtCompound());
				File regionFile = new File(folder, "s.%d.%d.dat".formatted(pos.x(), pos.z()));
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

	public Multimap<RegistryKey<Structure>, ChunkPos> readUpdatePacket(S2CStructuresAddedPacket packet) {
		if (Surveyor.CONFIG.structures == SystemMode.FROZEN) return HashMultimap.create();
		packet.starts().cellSet().forEach(c -> put(c.getRowKey(), c.getColumnKey(), c.getValue(), packet.types().get(c.getRowKey()), packet.tags().get(c.getRowKey())));
		return MapUtil.keyMultiMap(packet.starts());
	}

	public S2CStructuresAddedPacket createUpdatePacket(boolean shared, Multimap<RegistryKey<Structure>, ChunkPos> keySet) {
		Table<RegistryKey<Structure>, ChunkPos, StructureStartSummary> packetStructures = HashBasedTable.create();
		Map<RegistryKey<Structure>, RegistryKey<StructureType<?>>> packetTypes = new HashMap<>();
		Multimap<RegistryKey<Structure>, TagKey<Structure>> packetTags = HashMultimap.create();
		keySet.forEach((key, pos) -> packetStructures.put(key, pos, get(key, pos)));
		for (RegistryKey<Structure> key : keySet.keySet()) {
			packetTypes.put(key, getType(key));
			packetTags.putAll(key, getTags(key));
		}
		return new S2CStructuresAddedPacket(summary.dimension(), shared, packetStructures, packetTypes, packetTags);
	}

	public boolean isDirty() {
		return (dirty || regions.values().stream().anyMatch(RegionStructureSummary::isDirty)) && Surveyor.CONFIG.structures != SystemMode.FROZEN;
	}

	private void dirty() {
		dirty = true;
	}
}
