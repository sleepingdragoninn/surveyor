package folk.sisby.surveyor;

import folk.sisby.surveyor.client.SurveyorClient;
import folk.sisby.surveyor.config.SystemMode;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.structure.WorldStructures;
import folk.sisby.surveyor.terrain.WorldTerrain;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class WorldSummary {
	private static boolean ENABLE_TERRAIN = false;
	private static boolean ENABLE_STRUCTURES = false;
	private static boolean ENABLE_LANDMARKS = false;
	private final RegistryKey<World> dimension;
	private final DynamicRegistryManager manager;
	private final @Nullable MinecraftServer server;
	private final @Nullable WorldTerrain terrain;
	private final @Nullable WorldStructures structures;
	private final @Nullable WorldLandmarks landmarks;

	public WorldSummary(@Nullable MinecraftServer server, RegistryKey<World> dimension, DynamicRegistryManager manager, File folder) {
		this.dimension = dimension;
		this.manager = manager;
		this.server = server;
		boolean disableTerrain = (Surveyor.CONFIG.terrain == SystemMode.DISABLED || Surveyor.CONFIG.terrain == SystemMode.DYNAMIC && !ENABLE_TERRAIN && (server == null || server.isSingleplayer()));
		boolean disableStructures = (Surveyor.CONFIG.structures == SystemMode.DISABLED || Surveyor.CONFIG.structures == SystemMode.DYNAMIC && !ENABLE_STRUCTURES && (server == null || server.isSingleplayer()));
		boolean disableLandmarks = (Surveyor.CONFIG.landmarks == SystemMode.DISABLED || Surveyor.CONFIG.landmarks == SystemMode.DYNAMIC && !ENABLE_LANDMARKS && (server == null || server.isSingleplayer()));
		Surveyor.LOGGER.info("[Surveyor] Loading data for {}", dimension.getValue());
		if (!disableTerrain || !disableStructures || !disableLandmarks) folder.mkdirs();
		this.terrain = disableTerrain ? null : WorldTerrain.load(this, folder);
		this.structures = disableStructures ? null : WorldStructures.load(this, folder);
		this.landmarks = disableLandmarks ? null : WorldLandmarks.load(this, folder);
		Surveyor.LOGGER.info("[Surveyor] Finished loading data for {}", dimension.getValue());
	}

	public static WorldSummary of(World world) {
		if (world.isClient()) return SurveyorClient.tryGetSummary(world.getRegistryKey());
		ServerSummary summary = ServerSummary.of(world.getServer());
		return summary == null ? null : summary.getWorld(world.getRegistryKey());
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

	public void save(@Nullable World world, File folder, boolean suppressLogs) {
		if (!isDirty()) return;
		folder.mkdirs();
		int chunks = terrain == null ? 0 : terrain.save(world);
		int keys = structures == null ? 0 : structures.save(folder);
		int marks = landmarks == null ? 0 : landmarks.save(folder);
		if (!suppressLogs && (chunks > 0 || keys > 0 || marks > 0)) Surveyor.LOGGER.info("[Surveyor] Finished saving data for {} | cleaned {} terrain regions, {} structure regions, {} landmarks", dimension, chunks, keys, marks);
	}

	public boolean isDirty() {
		return (terrain != null && terrain.isDirty()) || (structures != null && structures.isDirty()) || (landmarks != null && landmarks.isDirty());
	}

	public boolean isClient() {
		return server == null;
	}

	public @Nullable World world() {
		return server != null ? server.getWorld(dimension) : SurveyorClient.getWorld(dimension);
	}

	public RegistryKey<World> dimension() {
		return dimension;
	}

	public DynamicRegistryManager manager() {
		return manager;
	}

	public @Nullable MinecraftServer server() {
		return server;
	}

	public @Nullable WorldTerrain terrain() {
		return terrain;
	}

	public @Nullable WorldStructures structures() {
		return structures;
	}

	public @Nullable WorldLandmarks landmarks() {
		return landmarks;
	}
}
