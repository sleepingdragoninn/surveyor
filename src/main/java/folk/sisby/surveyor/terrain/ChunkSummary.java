package folk.sisby.surveyor.terrain;

import folk.sisby.surveyor.util.ChunkUtil;
import folk.sisby.surveyor.util.RegistryPalette;
import folk.sisby.surveyor.util.uints.UInts;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class ChunkSummary {
	public static final int MINIMUM_AIR_DEPTH = 2;
	public static final String KEY_AIR_COUNT = "air";
	public static final String KEY_LAYERS = "layers";

	protected final Integer airCount;
	protected final TreeMap<Integer, @Nullable LayerSummary> layers = new TreeMap<>();

	public ChunkSummary(World world, WorldChunk chunk, int[] layerHeights, RegistryPalette<Biome> biomePalette, RegistryPalette<Block> blockPalette, boolean countAir) {
		this.airCount = countAir ? ChunkUtil.airCount(chunk) : null;
		LayerSummary.FloorSummary[][] layerFloors = new LayerSummary.FloorSummary[layerHeights.length - 1][256];
		ChunkSection[] rawSections = chunk.getSectionArray();
		SectionSummary[] sections = new SectionSummary[rawSections.length];
		for (int i = 0; i < rawSections.length; i++) {
			sections[i] = SectionSummary.ofSection(rawSections[i]);
		}
		int chunkX = chunk.getPos().getStartX();
		int chunkZ = chunk.getPos().getStartZ();
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int walkspaceHeight = 2; // Start at 2 to allow finding floors at the height limit.
				int waterDepth = 0;
				Block carpetBlock = null;
				BlockPos carpetPos = new BlockPos(chunkX + x, Integer.MAX_VALUE, chunkZ + z);
				for (int layerIndex = 0; layerIndex < layerHeights.length - 1; layerIndex++) {
					LayerSummary.FloorSummary foundFloor = null;
					for (int y = layerHeights[layerIndex]; y > layerHeights[layerIndex + 1]; y--) {
						int sectionIndex = chunk.getSectionIndex(y);
						SectionSummary section = sections[sectionIndex];
						if (section == null) {
							int sectionBottom = ChunkSectionPos.getBlockCoord(chunk.sectionIndexToCoord(sectionIndex));
							walkspaceHeight += (y - sectionBottom + 1);
							waterDepth = 0;
							y = sectionBottom;
							continue;
						}
						BlockPos pos = new BlockPos(chunkX + x, y, chunkZ + z);
						BlockState state = section.getBlockState(x, y, z);
						Fluid fluid = state.getFluidState().getFluid();

						if (!state.isSolidBlock(world, pos) && fluid.matchesType(Fluids.EMPTY)) {
							walkspaceHeight++;
							waterDepth = 0;
							if (walkspaceHeight >= MINIMUM_AIR_DEPTH && state.getMapColor(world, pos) != MapColor.CLEAR) {
								carpetPos = pos;
								carpetBlock = state.getBlock();
							}
						} else if (fluid.matchesType(Fluids.WATER) || fluid.matchesType(Fluids.FLOWING_WATER)) { // keep walkspace when traversing water
							waterDepth++;
						} else { // Blocks Movement or Has Non-Water Fluid.
							if (foundFloor == null) {
								if (carpetPos.getY() == y + 1) {
									foundFloor = new LayerSummary.FloorSummary(carpetPos.getY(), biomePalette.findOrAdd(section.getBiomeEntry(x, carpetPos.getY(), z, world.getBottomY(), world.getTopYInclusive()).value()), blockPalette.findOrAdd(carpetBlock), world.getLightLevel(LightType.BLOCK, carpetPos), waterDepth, waterDepth == 0 ? 0 : world.getLightLevel(LightType.BLOCK, pos.up().up(waterDepth)));
									if (carpetPos.getY() > layerHeights[layerIndex]) { // Actually a floor for the layer above
										if (layerFloors[layerIndex - 1][x * 16 + z] == null) layerFloors[layerIndex - 1][x * 16 + z] = foundFloor;
										foundFloor = null;
									}
									// Carpeted glass needs to reset walkspaces
									walkspaceHeight = 0;
									waterDepth = 0;
								} else if (walkspaceHeight >= MINIMUM_AIR_DEPTH && state.getMapColor(world, pos) != MapColor.CLEAR) {
									int biome = biomePalette.findOrAdd(section.getBiomeEntry(x, y, z, world.getBottomY(), world.getTopYInclusive()).value());
									int block = blockPalette.findOrAdd(state.getBlock());
									foundFloor = new LayerSummary.FloorSummary(y, biome, block, world.getLightLevel(LightType.BLOCK, pos.up()), waterDepth, waterDepth == 0 ? 0 : world.getLightLevel(LightType.BLOCK, pos.up().up(waterDepth)));
								}
							}
							if (state.getMapColor(world, pos) != MapColor.CLEAR) { // Don't reset walkspace for glass/barriers/etc.
								walkspaceHeight = 0;
								waterDepth = 0; // Prevents a glass block on the ocean floor from hiding all the water
							}
						}
					}
					layerFloors[layerIndex][x * 16 + z] = foundFloor;
				}
			}
		}
		for (int i = 0; i < layerFloors.length; i++) {
			this.layers.put(layerHeights[i], LayerSummary.fromSummaries(layerFloors[i], layerHeights[i]));
		}
	}

	public ChunkSummary(NbtCompound nbt) {
		this.airCount = nbt.getInt(KEY_AIR_COUNT).isPresent() ? nbt.getInt(KEY_AIR_COUNT).get() : null;
		NbtCompound layersCompound = nbt.getCompound(KEY_LAYERS).orElse(new NbtCompound());
		for (String key : layersCompound.getKeys()) {
			int layerY = Integer.parseInt(key);
			layers.put(layerY, LayerSummary.fromNbt(layersCompound.getCompound(key).orElseThrow()));
		}
	}

	public ChunkSummary(PacketByteBuf buf) {
		layers.putAll(buf.readMap(PacketByteBuf::readVarInt, (b) -> {
			if (b.readByte() == 0) {
				return null;
			} else {
				return LayerSummary.fromBuf(buf);
			}
		}));
		this.airCount = -1;
	}

	public NbtCompound writeNbt(NbtCompound nbt) {
		if (this.airCount != null) nbt.putInt(KEY_AIR_COUNT, this.airCount);
		NbtCompound layersCompound = new NbtCompound();
		layers.forEach((layerY, layerSummary) -> {
			NbtCompound layerCompound = new NbtCompound();
			if (layerSummary != null) layerSummary.writeNbt(layerCompound);
			layersCompound.put(String.valueOf(layerY), layerCompound);
		});
		nbt.put(KEY_LAYERS, layersCompound);
		return nbt;
	}

	public void writeBuf(PacketByteBuf buf) {
		buf.writeMap(layers, PacketByteBuf::writeVarInt, (b, summary) -> {
			if (summary == null) {
				b.writeByte(0);
			} else {
				b.writeByte(1);
				summary.writeBuf(buf);
			}
		});
	}

	public void remap(Map<Integer, Integer> biomeRemap, Map<Integer, Integer> blockRemap) {
		Map<Integer, LayerSummary> newLayers = new HashMap<>();
		layers.forEach((y, layer) -> newLayers.put(y, layer == null ? null : new LayerSummary(layer.found, layer.depth, UInts.remap(layer.biome, biomeRemap::get, LayerSummary.BIOME_DEFAULT, layer.found.cardinality()), UInts.remap(layer.block, blockRemap::get, LayerSummary.BLOCK_DEFAULT, layer.found.cardinality()), layer.light, layer.water, layer.glint)));
		layers.clear();
		layers.putAll(newLayers);
	}

	public Integer getAirCount() {
		return airCount;
	}

	/**
	 * Gets an uncompressed layer of the topmost floor found for each X,Z column within the specified range.
	 *
	 * @param minY        the minimum (inclusive) height of floors to include in the layer.
	 * @param maxY        the maximum (inclusive) height of floors to include in the layer.
	 * @param worldHeight the maximum height of the world - or any layer height > maxY to be reused in LayerSummary#getHeight() later.
	 * @return A layer summary of top floors.
	 */
	public @Nullable LayerSummary.Raw toSingleLayer(Integer minY, Integer maxY, int worldHeight) {
		LayerSummary.Raw outRaw = new LayerSummary.Raw(new BitSet(256), new int[256], new int[256], new int[256], new int[256], new int[256], new int[256]);
		layers.descendingMap().forEach((y, layer) -> {
			if (layer != null) {
				layer.fillEmptyFloors(
					worldHeight - y,
					maxY == null ? Integer.MIN_VALUE : y - maxY,
					minY == null ? Integer.MAX_VALUE : y - minY,
					outRaw
				);
			}
		});
		return outRaw.exists().cardinality() == 0 ? null : outRaw;
	}

	public @Nullable LayerSummary.Raw toSingleLayerBelow(Integer minY, int[] depthsAbove, int worldHeight) {
		LayerSummary.Raw outRaw = new LayerSummary.Raw(new BitSet(256), new int[256], new int[256], new int[256], new int[256], new int[256], new int[256]);
		layers.descendingMap().forEach((y, layer) -> {
			if (layer != null) {
				layer.fillEmptyFloorsUnder(
					worldHeight - y,
					depthsAbove,
					minY == null ? Integer.MAX_VALUE : y - minY,
					outRaw
				);
			}
		});
		return outRaw.exists().cardinality() == 0 ? null : outRaw;
	}
}
