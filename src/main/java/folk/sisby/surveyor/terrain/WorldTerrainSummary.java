package folk.sisby.surveyor.terrain;

import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.SurveyorEvents;
import folk.sisby.surveyor.SurveyorExploration;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.config.SystemMode;
import folk.sisby.surveyor.util.ChunkUtil;
import folk.sisby.surveyor.util.RegionPos;
import folk.sisby.surveyor.util.RegistryPalette;
import net.minecraft.block.Block;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.WorldChunk;

import java.io.File;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WorldTerrainSummary {
	protected final RegistryKey<World> worldKey;
	protected final DynamicRegistryManager registryManager;
	protected final Map<RegionPos, RegionSummary> regions = new ConcurrentHashMap<>();
	protected final File folder;

	public WorldTerrainSummary(RegistryKey<World> worldKey, DynamicRegistryManager registryManager, Map<RegionPos, RegionSummary> regions, File folder) {
		this.worldKey = worldKey;
		this.registryManager = registryManager;
		this.regions.putAll(regions);
		this.folder = folder;
	}

	public static Set<ChunkPos> toKeys(Map<RegionPos, BitSet> bitSets) {
		return toKeys(bitSets, Comparator.comparingInt(pos -> pos.x() + pos.z()));
	}

	public static Set<ChunkPos> toKeys(Map<RegionPos, BitSet> bitSets, ChunkPos originChunk) {
		ChunkPos oPos = new ChunkPos(RegionPos.chunkToRegion(originChunk.x), RegionPos.chunkToRegion(originChunk.z));
		return toKeys(bitSets, Comparator.comparingDouble(pos -> (oPos.x - pos.x()) * (oPos.x - pos.x()) + (oPos.z - pos.z()) * (oPos.z - pos.z())));
	}

	public static Set<ChunkPos> toKeys(Map<RegionPos, BitSet> bitSets, Comparator<RegionPos> regionComparator) {
		Set<ChunkPos> set = new LinkedHashSet<>();
		bitSets.entrySet().stream().sorted(Map.Entry.comparingByKey(regionComparator)).forEach(e -> e.getValue().stream().forEach(i -> set.add(e.getKey().toChunk(i))));
		return set;
	}

	public static WorldTerrainSummary load(World world, File folder) {
		Map<RegionPos, RegionSummary> regions = new HashMap<>();
		ChunkUtil.getRegionFiles(folder, "c").forEach((pos, file) -> regions.put(pos, RegionSummary.fromFile(file, world.getRegistryManager(), pos)));
		return new WorldTerrainSummary(world.getRegistryKey(), world.getRegistryManager(), regions, folder);
	}

	public static void onChunkLoad(World world, WorldChunk chunk) {
		WorldSummary summary = WorldSummary.of(world);
		if (summary.terrain() != null && (!summary.terrain().contains(chunk.getPos()) || !Surveyor.CONFIG.lazyClientUpdating || !ChunkUtil.airCount(chunk).equals(summary.terrain().get(chunk.getPos()).getAirCount()))) {
			summary.terrain().put(world, chunk);
		}
	}

	public static void onChunkUnload(World world, WorldChunk chunk) {
		WorldSummary summary = WorldSummary.of(world);
		if (summary.terrain() != null && chunk.needsSaving()) {
			summary.terrain().put(world, chunk);
		}
	}

	public boolean contains(ChunkPos pos) {
		RegionPos regionPos = RegionPos.of(pos);
		return regions.containsKey(regionPos) && regions.get(regionPos).contains(pos, registryManager);
	}

	public ChunkSummary get(ChunkPos pos) {
		RegionPos regionPos = RegionPos.of(pos);
		return regions.containsKey(regionPos) ? regions.get(regionPos).get(pos, registryManager) : null;
	}

	public RegionSummary getRegion(RegionPos regionPos) {
		return regions.computeIfAbsent(regionPos, k -> RegionSummary.fromEmpty(folder, regionPos, registryManager));
	}

	public RegistryPalette<Biome>.ValueView getBiomePalette(ChunkPos pos) {
		RegionPos regionPos = RegionPos.of(pos);
		return regions.get(regionPos).getBiomePalette();
	}

	public RegistryPalette<Block>.ValueView getBlockPalette(ChunkPos pos) {
		RegionPos regionPos = RegionPos.of(pos);
		return regions.get(regionPos).getBlockPalette();
	}

	public Map<RegionPos, BitSet> bitSet(SurveyorExploration exploration) {
		Map<RegionPos, BitSet> map = new HashMap<>();
		regions.forEach((p, r) -> map.put(p, r.bitSet(registryManager)));
		return exploration == null ? map : exploration.limitTerrainBitset(worldKey, map);
	}

	public void put(World world, WorldChunk chunk) {
		if (Surveyor.CONFIG.terrain == SystemMode.FROZEN) return;
		regions.computeIfAbsent(RegionPos.of(chunk.getPos()), k -> RegionSummary.fromEmpty(folder, RegionPos.of(chunk.getPos()), registryManager)).putChunk(world, chunk);
		SurveyorEvents.Invoke.terrainUpdated(world, chunk.getPos());
	}

	public int save(World world) {
		List<RegionPos> savedRegions = new ArrayList<>();
		regions.forEach((pos, summary) -> {
			if (summary.isLoaded()) {
				if (summary.isDirty()) savedRegions.add(pos);
				summary.save(world.getRegistryManager(), summary.isUnloaded(world));
			}
		});
		return savedRegions.size();
	}

	public boolean isDirty() {
		return regions.values().stream().anyMatch(RegionSummary::isDirty);
	}
}
