package folk.sisby.surveyor.terrain;

import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.SurveyorEvents;
import folk.sisby.surveyor.SurveyorExploration;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.config.SystemMode;
import folk.sisby.surveyor.packet.S2CUpdateRegionPacket;
import folk.sisby.surveyor.util.ChunkUtil;
import folk.sisby.surveyor.util.RegionPos;
import folk.sisby.surveyor.util.RegistryPalette;
import net.minecraft.block.Block;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WorldTerrain {
	protected final WorldSummary summary;
	protected final DynamicRegistryManager registryManager;
	protected final Map<RegionPos, RegionSummary> regions = new ConcurrentHashMap<>();
	protected final File folder;
	protected final Map<RegionPos, Map<UUID, BitSet>> queuedUpdates = new LinkedHashMap<>();

	public WorldTerrain(WorldSummary summary, DynamicRegistryManager registryManager, Map<RegionPos, RegionSummary> regions, File folder) {
		this.summary = summary;
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

	public static WorldTerrain load(WorldSummary summary, DynamicRegistryManager manager, File folder) {
		Map<RegionPos, RegionSummary> regions = new HashMap<>();
		ChunkUtil.getRegionFiles(folder, "c").forEach((pos, file) -> regions.put(pos, RegionSummary.fromFile(file, manager, pos)));
		return new WorldTerrain(summary, manager, regions, folder);
	}

	public static void onChunkLoad(World world, WorldChunk chunk) {
		WorldSummary summary = WorldSummary.of(world);
		if (summary.terrain() != null) {
			ChunkSummary chunkSummary = summary.terrain().get(chunk.getPos());
			if (chunkSummary == null || !Surveyor.CONFIG.lazyClientUpdating || !ChunkUtil.airCount(chunk).equals(chunkSummary.getAirCount())) {
				summary.terrain().put(world, chunk);
			}
		}
	}

	public static void onChunkUnload(World world, WorldChunk chunk) {
		WorldSummary summary = WorldSummary.of(world);
		if (summary.terrain() != null) {
			if (chunk.needsSaving()) {
				summary.terrain().put(world, chunk);
			}
		}
	}

	public boolean contains(ChunkPos pos) {
		RegionPos regionPos = RegionPos.of(pos);
		return regions.containsKey(regionPos) && regions.get(regionPos).contains(pos);
	}

	public ChunkSummary get(ChunkPos pos) {
		RegionPos regionPos = RegionPos.of(pos);
		return regions.containsKey(regionPos) ? regions.get(regionPos).get(pos) : null;
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
		regions.forEach((p, r) -> map.put(p, r.bitSet()));
		return exploration == null ? map : exploration.limit(summary.dimension(), map);
	}

	public void put(World world, WorldChunk chunk) {
		if (Surveyor.CONFIG.terrain == SystemMode.FROZEN) return;
		regions.computeIfAbsent(RegionPos.of(chunk.getPos()), k -> RegionSummary.fromEmpty(folder, RegionPos.of(chunk.getPos()), registryManager)).putChunk(world, chunk);
		SurveyorEvents.Invoke.terrainUpdated(WorldSummary.of(world), chunk.getPos());
	}

	public static void onTick(ServerWorld world) {
		WorldTerrain terrain = WorldSummary.of(world).terrain();
		if (terrain != null) terrain.serverTick(world);
	}

	public void sendUpdateForRegion(RegionPos regionPos, ServerPlayerEntity player, BitSet set) {
		RegionSummary region = getRegion(regionPos);
		SurveyorExploration personalExploration = SurveyorExploration.of(player);
		BitSet personalSet = personalExploration.limit(summary.dimension(), regionPos, (BitSet) set.clone());
		if (!personalSet.isEmpty()) S2CUpdateRegionPacket.of(summary.dimension(), false, regionPos, region, personalSet).send(player);
		set.andNot(personalSet);
		if (!set.isEmpty()) S2CUpdateRegionPacket.of(summary.dimension(), true, regionPos, region, set).send(player);
	}

	public void serverTick(ServerWorld world) {
		if ((world.getServer().getTicks() % Surveyor.CONFIG.networking.terrainTicks) != 0) return;
		queuedUpdates.keySet().stream().findFirst().ifPresent(regionPos -> {
			RegionSummary region = getRegion(regionPos);
			queuedUpdates.get(regionPos).forEach((uuid, set) -> {
				ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
				if (player != null) sendUpdateForRegion(regionPos, player, set);
			});
			queuedUpdates.remove(regionPos);
			if (region.isLoaded() && region.isUnloaded(world)) region.save(true);
		});
	}

	public void queueUpdate(RegionPos regionPos, BitSet set, ServerPlayerEntity player) {
		if (getRegion(regionPos).isLoaded()) {
			sendUpdateForRegion(regionPos, player, set);
		} else {
			queuedUpdates.computeIfAbsent(regionPos, k -> new LinkedHashMap<>()).put(player.getUuid(), set);
		}
	}

	public int save(@Nullable World world) {
		List<RegionPos> savedRegions = new ArrayList<>();
		regions.forEach((pos, summary) -> {
			if (summary.isLoaded()) {
				if (summary.isDirty()) savedRegions.add(pos);
				summary.save(world == null || summary.isUnloaded(world));
			}
		});
		return savedRegions.size();
	}

	public boolean isDirty() {
		return regions.values().stream().anyMatch(RegionSummary::isDirty);
	}
}
