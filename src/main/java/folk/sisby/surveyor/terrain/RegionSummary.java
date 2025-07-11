package folk.sisby.surveyor.terrain;

import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.config.SystemMode;
import folk.sisby.surveyor.packet.S2CUpdateRegionPacket;
import folk.sisby.surveyor.util.RegistryPalette;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class RegionSummary {
	public static final int REGION_POWER = 5;
	public static final int REGION_SIZE = 1 << REGION_POWER;
	public static final int BITSET_SIZE = 1 << (REGION_POWER * 2);
	public static final String KEY_BIOMES = "biomes";
	public static final String KEY_BLOCKS = "blocks";
	public static final String KEY_BIOME_WATER = "biomeWater";
	public static final String KEY_BIOME_FOLIAGE = "biomeFoliage";
	public static final String KEY_BIOME_GRASS = "biomeGrass";
	public static final String KEY_BLOCK_COLORS = "blockColors";
	public static final String KEY_CHUNKS = "chunks";

	protected final RegistryPalette<Biome> biomePalette;
	protected final RegistryPalette<Block> blockPalette;
	protected final ChunkSummary[][] chunks = new ChunkSummary[REGION_SIZE][REGION_SIZE];

	protected boolean dirty = false;

	public RegionSummary(DynamicRegistryManager manager) {
		biomePalette = new RegistryPalette<>(manager.getOrThrow(RegistryKeys.BIOME));
		blockPalette = new RegistryPalette<>(manager.getOrThrow(RegistryKeys.BLOCK));
	}

	public static <T, O> List<O> mapIterable(Iterable<T> palette, Function<T, O> mapper) {
		List<O> list = new ArrayList<>();
		for (T value : palette) {
			list.add(mapper.apply(value));
		}
		return list;
	}

	public static int regionToChunk(int xz) {
		return xz << REGION_POWER;
	}

	public static int chunkToRegion(int xz) {
		return xz >> REGION_POWER;
	}

	public static int regionRelative(int xz) {
		return xz & (RegionSummary.REGION_SIZE - 1);
	}

	public static int bitForXZ(int x, int z) {
		return (x << REGION_POWER) + z;
	}

	public static int bitForChunk(ChunkPos pos) {
		return bitForXZ(regionRelative(pos.x), regionRelative(pos.z));
	}

	public static int xForBit(int i) {
		return i >> REGION_POWER;
	}

	public static int zForBit(int i) {
		return i & (REGION_SIZE - 1);
	}

	public static ChunkPos chunkForBit(ChunkPos rPos, int i) {
		return new ChunkPos(regionToChunk(rPos.x) + xForBit(i), regionToChunk(rPos.z) + zForBit(i));
	}

	public static RegionSummary readNbt(NbtCompound nbt, DynamicRegistryManager manager, ChunkPos pos) {
		RegionSummary summary = new RegionSummary(manager);
		Registry<Biome> biomeRegistry = manager.getOrThrow(RegistryKeys.BIOME);
		Registry<Block> blockRegistry = manager.getOrThrow(RegistryKeys.BLOCK);
		NbtList biomeList = nbt.getList(KEY_BIOMES).orElse(new NbtList());
		Map<Integer, Integer> biomeRemap = new Int2IntArrayMap(biomeList.size());
		for (int i = 0; i < biomeList.size(); i++) {
			Identifier biomeId = Identifier.tryParse(biomeList.get(i).asString().orElseThrow());
			Biome biome = biomeRegistry.get(biomeId);
			Biome newBiome = biome == null ? biomeRegistry.get(BiomeKeys.THE_VOID) : biome;
			int newIndex = summary.biomePalette.findOrAdd(newBiome);
			if (biome == null || newIndex != i) {
				Surveyor.LOGGER.warn("[Surveyor] Remapping biome palette in region {}: {} (#{}) is now {} (#{})", pos, biomeId, i, biomeRegistry.getId(newBiome), newIndex);
				biomeRemap.put(i, newIndex);
				summary.dirty();
			}
		}
		NbtList blockList = nbt.getList(KEY_BLOCKS).orElse(new NbtList());
		Map<Integer, Integer> blockRemap = new Int2IntArrayMap(blockList.size());
		for (int i = 0; i < blockList.size(); i++) {
			Identifier blockId = Identifier.tryParse(blockList.get(i).asString().orElseThrow());
			Block block = blockRegistry.get(blockId);
			Block newBlock = block == null ? Blocks.AIR : block;
			int newIndex = summary.blockPalette.findOrAdd(newBlock);
			if (block == null || newIndex != i) {
				Surveyor.LOGGER.warn("[Surveyor] Remapping block palette in region {}: {} (#{}) is now {} (#{})", pos, blockList.get(i).asString(), i, blockRegistry.getId(newBlock), newIndex);
				blockRemap.put(i, newIndex);
				summary.dirty();
			}
		}
		NbtCompound chunksCompound = nbt.getCompound(KEY_CHUNKS).orElse(new NbtCompound());
		for (String posKey : chunksCompound.getKeys()) {
			int x = regionRelative(Integer.parseInt(posKey.split(",")[0]));
			int z = regionRelative(Integer.parseInt(posKey.split(",")[1]));
			summary.chunks[x][z] = new ChunkSummary(chunksCompound.getCompound(posKey).orElseThrow());
			if (!biomeRemap.isEmpty() || !blockRemap.isEmpty()) summary.chunks[x][z].remap(biomeRemap, blockRemap);
		}
		return summary;
	}

	public boolean contains(ChunkPos pos) {
		return chunks[regionRelative(pos.x)][regionRelative(pos.z)] != null;
	}

	public ChunkSummary get(ChunkPos pos) {
		return chunks[regionRelative(pos.x)][regionRelative(pos.z)];
	}

	public BitSet bitSet() {
		BitSet bitSet = new BitSet(BITSET_SIZE);
		for (int x = 0; x < REGION_SIZE; x++) {
			for (int z = 0; z < REGION_SIZE; z++) {
				if (chunks[x][z] != null) bitSet.set(bitForXZ(x, z));
			}
		}
		return bitSet;
	}

	public void putChunk(World world, WorldChunk chunk) {
		if (Surveyor.CONFIG.terrain == SystemMode.FROZEN) return;
		if (world.getHeight() == 0) return;
		chunks[regionRelative(chunk.getPos().x)][regionRelative(chunk.getPos().z)] = new ChunkSummary(world, chunk, DimensionSupport.getSummaryLayers(world), biomePalette, blockPalette, !(world instanceof ServerWorld));
		dirty();
	}

	public NbtCompound writeNbt(DynamicRegistryManager manager, NbtCompound nbt, ChunkPos regionPos) {
		Registry<Biome> biomeRegistry = manager.getOrThrow(RegistryKeys.BIOME);
		Registry<Block> blockRegistry = manager.getOrThrow(RegistryKeys.BLOCK);
		nbt.put(KEY_BIOMES, new NbtList(mapIterable(biomePalette.view(), b -> NbtString.of(biomeRegistry.getId(b).toString()))));
		nbt.put(KEY_BLOCKS, new NbtList(mapIterable(blockPalette.view(), b -> NbtString.of(blockRegistry.getId(b).toString()))));
		nbt.putIntArray(KEY_BIOME_WATER, mapIterable(biomePalette.view(), Biome::getWaterColor).stream().mapToInt(i -> i).toArray());
		nbt.putIntArray(KEY_BIOME_FOLIAGE, mapIterable(biomePalette.view(), Biome::getFoliageColor).stream().mapToInt(i -> i).toArray());
		nbt.putIntArray(KEY_BIOME_GRASS, mapIterable(biomePalette.view(), b -> b.getGrassColorAt(0, 0)).stream().mapToInt(i -> i).toArray());
		nbt.putIntArray(KEY_BLOCK_COLORS, mapIterable(blockPalette.view(), b -> b.getDefaultMapColor().color).stream().mapToInt(i -> i).toArray());
		NbtCompound chunksCompound = new NbtCompound();
		for (int x = 0; x < REGION_SIZE; x++) {
			for (int z = 0; z < REGION_SIZE; z++) {
				if (chunks[x][z] != null) chunksCompound.put("%s,%s".formatted((regionPos.x << REGION_POWER) + x, (regionPos.z << REGION_POWER) + z), chunks[x][z].writeNbt(new NbtCompound()));
			}
		}
		nbt.put(KEY_CHUNKS, chunksCompound);
		return nbt;
	}

	public BitSet readUpdatePacket(DynamicRegistryManager manager, S2CUpdateRegionPacket packet) {
		if (Surveyor.CONFIG.terrain == SystemMode.FROZEN) return new BitSet();
		Map<Integer, Integer> biomeRemap = new Int2IntArrayMap();
		for (int i = 0; i < packet.biomePalette().size(); i++) {
			biomeRemap.put(i, biomePalette.findOrAdd(packet.biomePalette().get(i)));
		}

		Map<Integer, Integer> blockRemap = new Int2IntArrayMap();
		for (int i = 0; i < packet.blockPalette().size(); i++) {
			blockRemap.put(i, blockPalette.findOrAdd(packet.blockPalette().get(i)));
		}
		int[] indices = packet.set().stream().toArray();
		for (int i = 0; i < packet.chunks().size(); i++) {
			ChunkSummary summary = packet.chunks().get(i);
			summary.remap(biomeRemap, blockRemap);
			this.chunks[xForBit(indices[i])][zForBit(indices[i])] = summary;
		}
		dirty();
		return packet.set();
	}

	public S2CUpdateRegionPacket createUpdatePacket(boolean shared, ChunkPos rPos, BitSet set) {
		return new S2CUpdateRegionPacket(shared, rPos, mapIterable(biomePalette, i -> i), mapIterable(blockPalette, i -> i), set, set.stream().mapToObj(i -> chunks[xForBit(i)][zForBit(i)]).toList());
	}

	public RegistryPalette<Biome>.ValueView getBiomePalette() {
		return biomePalette.view();
	}

	public RegistryPalette<Block>.ValueView getBlockPalette() {
		return blockPalette.view();
	}

	public boolean isDirty() {
		return dirty && Surveyor.CONFIG.terrain != SystemMode.FROZEN;
	}

	private void dirty() {
		dirty = true;
	}
}
