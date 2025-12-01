package folk.sisby.surveyor.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public record RegionPos(int x, int z) {
	public static final int CHUNK_POWER = 5;
	public static final int CHUNK_SIZE = 1 << CHUNK_POWER;
	public static final int CHUNK_AREA = CHUNK_SIZE * CHUNK_SIZE;

	public static final int BLOCK_POWER = 9;
	public static final int BLOCK_SIZE = 1 << BLOCK_POWER;
	public static final int BLOCK_AREA = BLOCK_SIZE * BLOCK_SIZE;

	public static int regionRelative(int xz) {
		return xz & (CHUNK_SIZE - 1);
	}

	public static int regionToChunk(int xz) {
		return xz << CHUNK_POWER;
	}

	public static int chunkToRegion(int xz) {
		return xz >> CHUNK_POWER;
	}

	public static int regionToBlock(int xz) {
		return xz << BLOCK_POWER;
	}

	public static int blockToRegion(int xz) {
		return xz >> BLOCK_POWER;
	}

	public static int chunkToBit(int relativeChunkX, int relativeChunkZ) {
		return (relativeChunkX << CHUNK_POWER) + relativeChunkZ;
	}

	public static int chunkToBit(ChunkPos pos) {
		return chunkToBit(regionRelative(pos.x), regionRelative(pos.z));
	}

	public static int bitToX(int bit) {
		return bit >> CHUNK_POWER;
	}

	public static int bitToZ(int bit) {
		return bit & (CHUNK_SIZE - 1);
	}

	public static RegionPos of(BlockPos pos) {
		return new RegionPos(blockToRegion(pos.getX()), blockToRegion(pos.getZ()));
	}

	public static RegionPos of(ChunkPos pos) {
		return new RegionPos(chunkToRegion(pos.x), chunkToRegion(pos.z));
	}

	public static RegionPos of(long pos) {
		return new RegionPos((int) pos, (int) (pos >> 32));
	}

	public static RegionPos of(String pos) {
		return new RegionPos(Integer.parseInt(pos.split(",")[0]), Integer.parseInt(pos.split(",")[1]));
	}

	@Override
	public @NotNull String toString() {
		return x + "," + z;
	}

	public int chunkX() {
		return regionToChunk(x);
	}

	public int chunkZ() {
		return regionToChunk(z);
	}

	public int blockX() {
		return regionToBlock(x);
	}

	public int blockZ() {
		return regionToBlock(z);
	}

	public long toLong() {
		return ChunkPos.toLong(x, z);
	}

	public ChunkPos toChunk() {
		return new ChunkPos(chunkX(), chunkZ());
	}

	public BlockPos toBlock(int y) {
		return new BlockPos(blockX(), y, blockZ());
	}

	public ChunkPos toChunk(int relativeChunkX, int relativeChunkZ) {
		return new ChunkPos(chunkX() + relativeChunkX, chunkZ() + relativeChunkZ);
	}

	public ChunkPos toChunk(int bit) {
		return toChunk(bitToX(bit), bitToZ(bit));
	}

	public Set<ChunkPos> toChunks(BitSet bits) {
		return bits.stream().mapToObj(this::toChunk).collect(Collectors.toSet());
	}

	public Set<ChunkPos> toChunks() {
		return IntStream.range(0, 256).mapToObj(this::toChunk).collect(Collectors.toSet());
	}

	public void forXZ(BiConsumer<Integer, Integer> action) {
		for (int x = 0; x < CHUNK_SIZE; x++) {
			for (int z = 0; z < CHUNK_SIZE; z++) {
				action.accept(x, z);
			}
		}
	}
}
