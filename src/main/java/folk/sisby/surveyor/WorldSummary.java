package folk.sisby.surveyor;

import folk.sisby.surveyor.config.SystemMode;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.structure.WorldStructureSummary;
import folk.sisby.surveyor.terrain.WorldTerrainSummary;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public record WorldSummary(@Nullable WorldTerrainSummary terrain, @Nullable WorldStructureSummary structures, @Nullable WorldLandmarks landmarks, boolean isClient) {
	private static boolean ENABLE_TERRAIN = false;
	private static boolean ENABLE_STRUCTURES = false;
	private static boolean ENABLE_LANDMARKS = false;

	public static WorldSummary of(World world) {
		return ((SurveyorWorld) world).surveyor$getSummary();
	}

	public static WorldSummary load(World world, File folder, boolean isClient) {
		boolean disableTerrain = (Surveyor.CONFIG.terrain == SystemMode.DISABLED || Surveyor.CONFIG.terrain == SystemMode.DYNAMIC && !ENABLE_TERRAIN && (isClient || world.getServer().isSingleplayer()));
		boolean disableStructures = (Surveyor.CONFIG.structures == SystemMode.DISABLED || Surveyor.CONFIG.structures == SystemMode.DYNAMIC && !ENABLE_STRUCTURES && (isClient || world.getServer().isSingleplayer()));
		boolean disableLandmarks = (Surveyor.CONFIG.landmarks == SystemMode.DISABLED || Surveyor.CONFIG.landmarks == SystemMode.DYNAMIC && !ENABLE_LANDMARKS && (isClient || world.getServer().isSingleplayer()));
		if (disableTerrain && disableStructures && disableLandmarks) return new WorldSummary(null, null, null, isClient);
		Surveyor.LOGGER.info("[Surveyor] Loading data for {}", world.getRegistryKey().getValue());
		folder.mkdirs();
		WorldTerrainSummary terrain = disableTerrain ? null : WorldTerrainSummary.load(world, folder);
		WorldStructureSummary structures = disableStructures ? null : WorldStructureSummary.load(world, folder);
		WorldLandmarks landmarks = disableLandmarks ? null : WorldLandmarks.load(world, folder);
		Surveyor.LOGGER.info("[Surveyor] Finished loading data for {}", world.getRegistryKey().getValue());
		return new WorldSummary(terrain, structures, landmarks, isClient);
	}

	public static void enableTerrain() {
		ENABLE_TERRAIN = true;
	}

	public static void enableStructures() {
		ENABLE_STRUCTURES = true;
	}

	public static void enableLandmarks() {
		ENABLE_LANDMARKS = true;
	}

	public void save(World world, File folder, boolean suppressLogs) {
		if (!isDirty()) return;
		folder.mkdirs();
		int chunks = terrain == null ? 0 : terrain.save(world);
		int keys = structures == null ? 0 : structures.save(world, folder);
		int marks = landmarks == null ? 0 : landmarks.save(world, folder);
		if (!suppressLogs && (chunks > 0 || keys > 0 || marks > 0)) Surveyor.LOGGER.info("[Surveyor] Finished saving data for {} | cleaned {} terrain regions, {} structure regions, {} landmarks", world.getRegistryKey().getValue(), chunks, keys, marks);
	}

	public boolean isDirty() {
		return (terrain != null && terrain.isDirty()) || (structures != null && structures.isDirty()) || (landmarks != null && landmarks.isDirty());
	}
}
