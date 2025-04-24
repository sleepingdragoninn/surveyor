package folk.sisby.surveyor.util;

import folk.sisby.surveyor.Surveyor;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtCrashException;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ChunkUtil {
	public static Integer airCount(Chunk chunk) {
		return Arrays.stream(chunk.getSectionArray()).mapToInt(s -> 4096 - s.nonEmptyBlockCount).sum();
	}

	public static File[] getRegionFiles(File folder, String prefix) {
		return folder.listFiles((file, name) -> {
			String[] split = name.split("\\.");
			if (split.length == 4 && split[0].equals(prefix) && split[3].equals("dat")) {
				try {
					Integer.parseInt(split[1]);
					Integer.parseInt(split[2]);
					return true;
				} catch (NumberFormatException ignored) {
				}
			}
			return false;
		});
	}

	public static Map<ChunkPos, NbtCompound> getRegionNbt(File folder, String prefix) {
		File[] regionFiles = getRegionFiles(folder, prefix);
		Map<ChunkPos, NbtCompound> regions = new HashMap<>();
		if (regionFiles != null) {
			for (File regionFile : regionFiles) {
				ChunkPos regionPos = new ChunkPos(Integer.parseInt(regionFile.getName().split("\\.")[1]), Integer.parseInt(regionFile.getName().split("\\.")[2]));
				NbtCompound regionCompound = null;
				try {
					regionCompound = NbtIo.readCompressed(regionFile.toPath(), NbtSizeTracker.ofUnlimitedBytes());
				} catch (IOException | NbtCrashException e) {
					Surveyor.LOGGER.error("[Surveyor] Error loading region nbt file {}.", regionFile.getName(), e);
				}
				if (regionCompound != null) regions.put(regionPos, regionCompound);
			}
		}
		return regions;
	}
}
