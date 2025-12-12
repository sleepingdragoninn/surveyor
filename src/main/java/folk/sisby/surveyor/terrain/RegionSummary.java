package folk.sisby.surveyor.terrain;

import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.config.SystemMode;
import folk.sisby.surveyor.packet.S2CUpdateRegionPacket;
import folk.sisby.surveyor.util.RegionPos;
import folk.sisby.surveyor.util.RegistryPalette;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtCrashException;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class RegionSummary {
	public static final String KEY_BIOMES = "biomes";
	public static final String KEY_BLOCKS = "blocks";
	public static final String KEY_BIOME_WATER = "biomeWater";
	public static final String KEY_BIOME_FOLIAGE = "biomeFoliage";
	public static final String KEY_BIOME_GRASS = "biomeGrass";
	public static final String KEY_BLOCK_COLORS = "blockColors";
	public static final String KEY_CHUNKS = "chunks";

	protected final RegionPos regionPos;
	protected final File saveFile;
	protected final DynamicRegistryManager manager;
	protected @Nullable RegistryPalette<Biome> biomePalette;
	protected @Nullable RegistryPalette<Block> blockPalette;
	protected @Nullable ChunkSummary[][] chunks;
	protected @Nullable BitSet bitSet;

	protected boolean dirty = false;
	protected boolean saving = false;

	private RegionSummary(DynamicRegistryManager manager, File saveFile, RegionPos regionPos, ChunkSummary[][] chunks, BitSet bitSet, RegistryPalette<Biome> biomePalette, RegistryPalette<Block> blockPalette) {
		this.manager = manager;
		this.biomePalette = biomePalette;
		this.blockPalette = blockPalette;
		this.saveFile = saveFile;
		this.regionPos = regionPos;
		this.chunks = chunks;
		this.bitSet = bitSet;
		if (bitSet == null) readNbt(regionPos, true);
	}

	public static <T, O> List<O> mapIterable(Iterable<T> palette, Function<T, O> mapper) {
		List<O> list = new ArrayList<>();
		for (T value : palette) {
			list.add(mapper.apply(value));
		}
		return list;
	}

	public static RegionSummary fromEmpty(File folder, RegionPos rPos, DynamicRegistryManager manager) {
		return new RegionSummary(manager, new File(folder, "c.%d.%d.dat".formatted(rPos.x(), rPos.z())), rPos, new ChunkSummary[RegionPos.CHUNK_SIZE][RegionPos.CHUNK_SIZE], new BitSet(RegionPos.CHUNK_AREA), new RegistryPalette<>(manager.getOrThrow(RegistryKeys.BIOME)), new RegistryPalette<>(manager.getOrThrow(RegistryKeys.BLOCK)));
	}

	public static RegionSummary fromFile(File file, DynamicRegistryManager manager, RegionPos rPos) {
		return new RegionSummary(manager, file, rPos, null, null, null, null);
	}

	protected void readNbt(RegionPos pos, boolean bitsOnly) {
		Registry<Biome> biomeRegistry = manager.getOrThrow(RegistryKeys.BIOME);
		Registry<Block> blockRegistry = manager.getOrThrow(RegistryKeys.BLOCK);
		NbtCompound nbt = new NbtCompound();
		try {
			nbt = NbtIo.readCompressed(saveFile.toPath(), NbtSizeTracker.ofUnlimitedBytes());
		} catch (IOException | NbtCrashException e) {
			Surveyor.LOGGER.error("[Surveyor] Error reading region summary file {}.", saveFile.getName(), e);
		}
		NbtCompound chunksCompound = nbt.getCompound(KEY_CHUNKS).orElse(new NbtCompound());
		bitSet = new BitSet(RegionPos.CHUNK_AREA);
		if (bitsOnly) {
			for (String posKey : chunksCompound.getKeys()) {
				int x = RegionPos.regionRelative(Integer.parseInt(posKey.split(",")[0]));
				int z = RegionPos.regionRelative(Integer.parseInt(posKey.split(",")[1]));
				bitSet.set(RegionPos.chunkToBit(x, z));
			}
			return;
		}
		this.biomePalette = new RegistryPalette<>(manager.getOrThrow(RegistryKeys.BIOME));
		this.blockPalette = new RegistryPalette<>(manager.getOrThrow(RegistryKeys.BLOCK));
		this.chunks = new ChunkSummary[RegionPos.CHUNK_SIZE][RegionPos.CHUNK_SIZE];
		NbtList biomeList = nbt.getList(KEY_BIOMES).orElse(new NbtList());
		Map<Integer, Integer> biomeRemap = new Int2IntArrayMap(biomeList.size());
		for (int i = 0; i < biomeList.size(); i++) {
			Identifier biomeId = Identifier.tryParse(biomeList.get(i).asString().orElseThrow());
			Biome biome = biomeRegistry.get(biomeId);
			Biome newBiome = biome == null ? biomeRegistry.get(BiomeKeys.THE_VOID) : biome;
			int newIndex = biomePalette.findOrAdd(newBiome);
			if (biome == null || newIndex != i) {
				if (biome == null) Surveyor.LOGGER.warn("[Surveyor] Remapping biome palette in region {}: {} (#{}) is now {} (#{})", pos, biomeId, i, biomeRegistry.getId(newBiome), newIndex);
				biomeRemap.put(i, newIndex);
				dirty();
			}
		}
		NbtList blockList = nbt.getList(KEY_BLOCKS).orElse(new NbtList());
		Map<Integer, Integer> blockRemap = new Int2IntArrayMap(blockList.size());
		for (int i = 0; i < blockList.size(); i++) {
			Identifier blockId = Identifier.tryParse(blockList.get(i).asString().orElseThrow());
			Block block = blockRegistry.get(blockId);
			Block newBlock = block == null ? Blocks.AIR : block;
			int newIndex = blockPalette.findOrAdd(newBlock);
			if (block == null || newIndex != i) {
				if (block == null) Surveyor.LOGGER.warn("[Surveyor] Remapping block palette in region {}: {} (#{}) is now {} (#{})", pos, blockList.get(i).asString(), i, blockRegistry.getId(newBlock), newIndex);
				blockRemap.put(i, newIndex);
				dirty();
			}
		}
		if (biomePalette.view().size() == 0 || blockPalette.view().size() == 0) { // abandon ship
			Surveyor.LOGGER.warn("[Surveyor] Palette was empty in region {}, skipping data load!", regionPos);
			return;
		}
		for (String posKey : chunksCompound.getKeys()) {
			int x = RegionPos.regionRelative(Integer.parseInt(posKey.split(",")[0]));
			int z = RegionPos.regionRelative(Integer.parseInt(posKey.split(",")[1]));
			ChunkSummary summary = new ChunkSummary(chunksCompound.getCompound(posKey).orElseThrow());
			set(x, z, summary);
			if (!biomeRemap.isEmpty() || !blockRemap.isEmpty()) summary.remap(biomeRemap, blockRemap);
		}
	}

	public boolean contains(ChunkPos pos) {
		return bitSet().get(RegionPos.chunkToBit(pos));
	}

	public ChunkSummary get(ChunkPos pos) {
		return get(RegionPos.regionRelative(pos.x), RegionPos.regionRelative(pos.z));
	}

	protected @Nullable ChunkSummary get(int x, int z) {
		if (chunks == null) readNbt(regionPos, false);
		return chunks[x][z];
	}

	protected void set(int x, int z, ChunkSummary summary) {
		if (chunks == null || bitSet == null) readNbt(regionPos, false);
		chunks[x][z] = summary;
		bitSet.set(RegionPos.chunkToBit(x, z), summary != null);
	}

	public BitSet bitSet() {
		if (bitSet == null) readNbt(regionPos, true);
		return (BitSet) bitSet.clone();
	}

	public void putChunk(World world, WorldChunk chunk) {
		if (Surveyor.CONFIG.terrain == SystemMode.FROZEN) return;
		if (world.getHeight() == 0) return;
		if (chunks == null) readNbt(regionPos, false);
		set(RegionPos.regionRelative(chunk.getPos().x), RegionPos.regionRelative(chunk.getPos().z), new ChunkSummary(world, chunk, DimensionSupport.getSummaryLayers(world), biomePalette, blockPalette, !(world instanceof ServerWorld)));
		dirty();
	}

	public boolean isUnloaded(World world) {
		return regionPos.toChunks().stream().noneMatch(c -> world.isChunkLoaded(c.x, c.z));
	}

	public void save(boolean unload) {
		if (saving) return;
		if (!isDirty()) {
			if (unload) chunks = null;
			return;
		}
		NbtCompound nbt = new NbtCompound();
		Registry<Biome> biomeRegistry = manager.getOrThrow(RegistryKeys.BIOME);
		Registry<Block> blockRegistry = manager.getOrThrow(RegistryKeys.BLOCK);
		nbt.put(KEY_BIOMES, new NbtList(mapIterable(biomePalette.view(), b -> NbtString.of(biomeRegistry.getId(b).toString()))));
		nbt.put(KEY_BLOCKS, new NbtList(mapIterable(blockPalette.view(), b -> NbtString.of(blockRegistry.getId(b).toString()))));
		nbt.putIntArray(KEY_BIOME_WATER, mapIterable(biomePalette.view(), Biome::getWaterColor).stream().mapToInt(i -> i).toArray());
		nbt.putIntArray(KEY_BIOME_FOLIAGE, mapIterable(biomePalette.view(), Biome::getFoliageColor).stream().mapToInt(i -> i).toArray());
		nbt.putIntArray(KEY_BIOME_GRASS, mapIterable(biomePalette.view(), b -> b.getGrassColorAt(0, 0)).stream().mapToInt(i -> i).toArray());
		nbt.putIntArray(KEY_BLOCK_COLORS, mapIterable(blockPalette.view(), b -> b.getDefaultMapColor().color).stream().mapToInt(i -> i).toArray());
		NbtCompound chunksCompound = new NbtCompound();
		regionPos.forXZ((x, z) -> {
			ChunkSummary chunk = get(x, z);
			if (chunk != null) {
				ChunkPos pos = regionPos.toChunk(x, z);
				chunksCompound.put("%s,%s".formatted(pos.x, pos.z), chunk.writeNbt(new NbtCompound()));
			}
		});
		nbt.put(KEY_CHUNKS, chunksCompound);
		dirty = false;
		saving = true;
		Util.getIoWorkerExecutor().execute(() -> {
			try {
				NbtIo.writeCompressed(nbt, saveFile.toPath());
			} catch (IOException e) {
				Surveyor.LOGGER.error("[Surveyor] Error writing region summary file {}.", saveFile.getName(), e);
			} finally {
				if (unload && !dirty) {
					chunks = null;
				}
				saving = false;
			}
		});
	}

	public BitSet readUpdatePacket(S2CUpdateRegionPacket packet) {
		if (Surveyor.CONFIG.terrain == SystemMode.FROZEN) return new BitSet();
		if (chunks == null) readNbt(regionPos, false);
		if (packet.biomePalette().isEmpty() || packet.blockPalette().isEmpty()) return new BitSet(); // nonsense
		Registry<Biome> biomeRegistry = manager.getOrThrow(RegistryKeys.BIOME);
		Registry<Block> blockRegistry = manager.getOrThrow(RegistryKeys.BLOCK);
		Map<Integer, Integer> biomeRemap = new Int2IntArrayMap();
		for (int i = 0; i < packet.biomePalette().size(); i++) {
			biomeRemap.put(i, biomePalette.findOrAdd(biomeRegistry.get(packet.biomePalette().get(i))));
		}

		Map<Integer, Integer> blockRemap = new Int2IntArrayMap();
		for (int i = 0; i < packet.blockPalette().size(); i++) {
			blockRemap.put(i, blockPalette.findOrAdd(blockRegistry.get(packet.blockPalette().get(i))));
		}
		int[] indices = packet.set().stream().toArray();
		for (int i = 0; i < packet.chunks().size(); i++) {
			ChunkSummary summary = packet.chunks().get(i);
			summary.remap(biomeRemap, blockRemap);
			set(RegionPos.bitToX(indices[i]), RegionPos.bitToZ(indices[i]), summary);
		}
		dirty();
		return packet.set();
	}

	public S2CUpdateRegionPacket createUpdatePacket(boolean shared, RegionPos rPos, BitSet set) {
		if (chunks == null) readNbt(regionPos, false);
		return new S2CUpdateRegionPacket(shared, rPos, mapIterable(biomePalette, i -> i), mapIterable(blockPalette, i -> i), set, set.stream().mapToObj(i -> get(RegionPos.bitToX(i), RegionPos.bitToZ(i))).toList());
	}

	public RegistryPalette<Biome>.ValueView getBiomePalette() {
		if (chunks == null) readNbt(regionPos, false);
		return biomePalette.view();
	}

	public RegistryPalette<Block>.ValueView getBlockPalette() {
		if (chunks == null) readNbt(regionPos, false);
		return blockPalette.view();
	}

	public boolean isLoaded() {
		return chunks != null;
	}

	public boolean isDirty() {
		return dirty && Surveyor.CONFIG.terrain != SystemMode.FROZEN;
	}

	private void dirty() {
		dirty = true;
	}
}
