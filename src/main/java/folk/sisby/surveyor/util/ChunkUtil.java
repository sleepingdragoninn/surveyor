package folk.sisby.surveyor.util;

import folk.sisby.surveyor.Surveyor;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ChunkUtil {
	public static Integer airCount(Chunk chunk) {
		return Arrays.stream(chunk.getSectionArray()).mapToInt(s -> 4096 - s.nonEmptyBlockCount).sum();
	}

	public static Map<ChunkPos, File> getRegionFiles(File folder, String prefix) {
		Map<ChunkPos, File> files = new HashMap<>();
		for (File file : Objects.requireNonNullElse(folder.listFiles(), new File[0])) {
				String[] split = file.getName().split("\\.");
				if (split.length == 4 && split[0].equals(prefix) && split[3].equals("dat")) {
					try {
						files.put(new ChunkPos(Integer.parseInt(split[1]), Integer.parseInt(split[2])), file);
					} catch (NumberFormatException ignored) {
					}
				}
			}
		return files;
	}

	public static Map<ChunkPos, NbtCompound> getRegionNbt(File folder, String prefix) {
		Map<ChunkPos, File> regionFiles = getRegionFiles(folder, prefix);
		Map<ChunkPos, NbtCompound> regions = new HashMap<>();
		for (ChunkPos regionPos : regionFiles.keySet()) {
			NbtCompound regionCompound = null;
			try {
				regionCompound = NbtIo.readCompressed(regionFiles.get(regionPos));
			} catch (IOException | CrashException e) {
				Surveyor.LOGGER.error("[Surveyor] Error loading region nbt file {}.", regionFiles.get(regionPos).getName(), e);
			}
			if (regionCompound != null) regions.put(regionPos, regionCompound);
		}
		return regions;
	}
}
