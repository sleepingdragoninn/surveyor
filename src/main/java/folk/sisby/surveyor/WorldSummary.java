package folk.sisby.surveyor;

import folk.sisby.surveyor.client.SurveyorClient;
import folk.sisby.surveyor.config.SystemMode;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.structure.WorldStructureSummary;
import folk.sisby.surveyor.terrain.WorldTerrainSummary;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public record WorldSummary(RegistryKey<World> worldKey, @Nullable MinecraftServer server, @Nullable WorldTerrainSummary terrain, @Nullable WorldStructureSummary structures, @Nullable WorldLandmarks landmarks) {
	private static boolean ENABLE_TERRAIN = false;
	private static boolean ENABLE_STRUCTURES = false;
	private static boolean ENABLE_LANDMARKS = false;

	public static WorldSummary of(World world) {
		if (world.isClient()) return SurveyorClient.getSummary(world.getRegistryKey(), world.getRegistryManager(), Surveyor.getBiomeSeed(world));
		return ((SurveyorWorld) world).surveyor$getSummary();
	}

	public static WorldSummary load(MinecraftServer server, RegistryKey<World> dim, DynamicRegistryManager manager, File folder) {
		boolean disableTerrain = (Surveyor.CONFIG.terrain == SystemMode.DISABLED || Surveyor.CONFIG.terrain == SystemMode.DYNAMIC && !ENABLE_TERRAIN && (server == null || server.isSingleplayer()));
		boolean disableStructures = (Surveyor.CONFIG.structures == SystemMode.DISABLED || Surveyor.CONFIG.structures == SystemMode.DYNAMIC && !ENABLE_STRUCTURES && (server == null || server.isSingleplayer()));
		boolean disableLandmarks = (Surveyor.CONFIG.landmarks == SystemMode.DISABLED || Surveyor.CONFIG.landmarks == SystemMode.DYNAMIC && !ENABLE_LANDMARKS && (server == null || server.isSingleplayer()));
		if (disableTerrain && disableStructures && disableLandmarks) return new WorldSummary(dim, server, null, null, null);
		Surveyor.LOGGER.info("[Surveyor] Loading data for {}", dim.getValue());
		folder.mkdirs();
		WorldTerrainSummary terrain = disableTerrain ? null : WorldTerrainSummary.load(dim, manager, folder);
		WorldStructureSummary structures = disableStructures ? null : WorldStructureSummary.load(dim, manager, folder);
		WorldLandmarks landmarks = disableLandmarks ? null : WorldLandmarks.load(dim, manager, folder, server == null);
		Surveyor.LOGGER.info("[Surveyor] Finished loading data for {}", dim.getValue());
		return new WorldSummary(dim, server, terrain, structures, landmarks);
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

	public @Nullable World world() {
		return server != null ? server.getWorld(worldKey) : SurveyorClient.getWorld(worldKey);
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

	public boolean isClient() {
		return server == null;
	}
}
