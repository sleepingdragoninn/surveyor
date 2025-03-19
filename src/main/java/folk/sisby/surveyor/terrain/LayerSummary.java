package folk.sisby.surveyor.terrain;

import folk.sisby.surveyor.util.uints.UInts;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;

public class LayerSummary {
	public static final String KEY_FOUND = "found";
	public static final String KEY_DEPTH = "depth";
	public static final String KEY_BIOME = "biome";
	public static final String KEY_BLOCK = "block";
	public static final String KEY_LIGHT = "light";
	public static final String KEY_WATER = "water";
	public static final String KEY_GLINT = "glint";

	public static final int DEPTH_DEFAULT = 0;
	public static final int BIOME_DEFAULT = 0;
	public static final int BLOCK_DEFAULT = 0;
	public static final int LIGHT_DEFAULT = 0;
	public static final int WATER_DEFAULT = 0;
	public static final int GLINT_DEFAULT = 0;

	protected final @NotNull BitSet found;
	protected final @Nullable UInts depth;
	protected final @Nullable UInts biome;
	protected final @Nullable UInts block;
	protected final @Nullable UInts light;
	protected final @Nullable UInts water;
	protected final @Nullable UInts glint;

	protected LayerSummary(@NotNull BitSet found, @Nullable UInts depth, @Nullable UInts biome, @Nullable UInts block, @Nullable UInts light, @Nullable UInts water, @Nullable UInts glint) {
		this.found = found;
		this.depth = depth;
		this.biome = biome;
		this.block = block;
		this.light = light;
		this.water = water;
		this.glint = glint;
	}

	public static LayerSummary fromSummaries(FloorSummary[] floorSummaries, int layerY) {
		BitSet found = new BitSet(256);
		for (int i = 0; i < floorSummaries.length; i++) {
			found.set(i, floorSummaries[i] != null);
		}
		int cardinality = found.cardinality();
		if (cardinality == 0) return null;
		int[] depth = new int[cardinality];
		int[] biome = new int[cardinality];
		int[] block = new int[cardinality];
		int[] light = new int[cardinality];
		int[] water = new int[cardinality];
		int[] glint = new int[cardinality];
		int c = 0;
		for (int i = 0; i < floorSummaries.length; i++) {
			if (found.get(i)) {
				FloorSummary summary = floorSummaries[i];
				depth[c] = layerY - summary.y;
				biome[c] = summary.biome;
				block[c] = summary.block;
				light[c] = summary.lightLevel;
				water[c] = summary.fluidDepth;
				glint[c] = summary.waterLight;
				c++;
			}
		}
		return new LayerSummary(
			found,
			UInts.fromUInts(depth, DEPTH_DEFAULT),
			UInts.fromUInts(biome, BIOME_DEFAULT),
			UInts.fromUInts(block, BLOCK_DEFAULT),
			UInts.fromUInts(light, LIGHT_DEFAULT),
			UInts.fromUInts(water, WATER_DEFAULT),
			UInts.fromUInts(glint, GLINT_DEFAULT)
		);
	}

	public static LayerSummary fromNbt(NbtCompound nbt) {
		if (!nbt.contains(KEY_FOUND)) return null;
		BitSet found = BitSet.valueOf(nbt.getLongArray(KEY_FOUND));
		int cardinality = found.cardinality();
		UInts depth = UInts.readNbt(nbt.get(KEY_DEPTH), cardinality);
		UInts biome = UInts.readNbt(nbt.get(KEY_BIOME), cardinality);
		UInts block = UInts.readNbt(nbt.get(KEY_BLOCK), cardinality);
		UInts light = UInts.readNbt(nbt.get(KEY_LIGHT), cardinality);
		UInts water = UInts.readNbt(nbt.get(KEY_WATER), cardinality);
		UInts glint = UInts.readNbt(nbt.get(KEY_GLINT), cardinality);
		return new LayerSummary(found, depth, biome, block, light, water, glint);
	}

	public static LayerSummary fromBuf(PacketByteBuf buf) {
		BitSet found = buf.readBitSet(256);
		int cardinality = found.cardinality();
		return new LayerSummary(
			found,
			UInts.readBuf(buf, cardinality),
			UInts.readBuf(buf, cardinality),
			UInts.readBuf(buf, cardinality),
			UInts.readBuf(buf, cardinality),
			UInts.readBuf(buf, cardinality),
			UInts.readBuf(buf, cardinality)
		);
	}

	public NbtCompound writeNbt(NbtCompound nbt) {
		nbt.putLongArray(KEY_FOUND, found.toLongArray());
		if (depth != null) this.depth.writeNbt(nbt, KEY_DEPTH);
		if (biome != null) this.biome.writeNbt(nbt, KEY_BIOME);
		if (block != null) this.block.writeNbt(nbt, KEY_BLOCK);
		if (light != null) this.light.writeNbt(nbt, KEY_LIGHT);
		if (water != null) this.water.writeNbt(nbt, KEY_WATER);
		if (glint != null) this.glint.writeNbt(nbt, KEY_GLINT);
		return nbt;
	}

	public void writeBuf(PacketByteBuf buf) {
		buf.writeBitSet(found, 256);
		UInts.writeBuf(depth, buf);
		UInts.writeBuf(biome, buf);
		UInts.writeBuf(block, buf);
		UInts.writeBuf(light, buf);
		UInts.writeBuf(water, buf);
		UInts.writeBuf(glint, buf);
	}

	public void fillEmptyFloors(int depthOffset, int minDepth, int maxDepth, LayerSummary.Raw outLayer) {
		int i = 0;
		for (int j = 0; j < 256; j++) {
			if (found.get(j)) {
				int floorDepth = depth == null ? DEPTH_DEFAULT : depth.get(i);
				if (!outLayer.exists.get(j) && floorDepth >= minDepth && floorDepth <= maxDepth) {
					outLayer.exists.set(j);
					outLayer.depths[j] = floorDepth + depthOffset;
					outLayer.biomes[j] = biome == null ? BIOME_DEFAULT : biome.get(i);
					outLayer.blocks[j] = block == null ? BLOCK_DEFAULT : block.get(i);
					outLayer.lightLevels[j] = light == null ? LIGHT_DEFAULT : light.get(i);
					outLayer.waterDepths[j] = water == null ? WATER_DEFAULT : water.get(i);
					outLayer.waterLights[j] = glint == null ? GLINT_DEFAULT : glint.get(i);
				}
				i++;
			}
		}
	}

	public void fillEmptyFloorsUnder(int depthOffset, int[] minRawDepth, int maxDepth, LayerSummary.Raw outLayer) {
		int i = 0;
		for (int j = 0; j < 256; j++) {
			if (found.get(j)) {
				int floorDepth = depth == null ? DEPTH_DEFAULT : depth.get(i);
				if (!outLayer.exists.get(j) && floorDepth > minRawDepth[j] - depthOffset && floorDepth <= maxDepth) {
					outLayer.exists.set(j);
					outLayer.depths[j] = floorDepth + depthOffset;
					outLayer.biomes[j] = biome == null ? BIOME_DEFAULT : biome.get(i);
					outLayer.blocks[j] = block == null ? BLOCK_DEFAULT : block.get(i);
					outLayer.lightLevels[j] = light == null ? LIGHT_DEFAULT : light.get(i);
					outLayer.waterDepths[j] = water == null ? WATER_DEFAULT : water.get(i);
					outLayer.waterLights[j] = glint == null ? GLINT_DEFAULT : glint.get(i);
				}
				i++;
			}
		}
	}

	public record Raw(BitSet exists, int[] depths, int[] biomes, int[] blocks, int[] lightLevels, int[] waterDepths, int[] waterLights) {
	}

	public record FloorSummary(int y, int biome, int block, int lightLevel, int fluidDepth, int waterLight) {
	}
}
