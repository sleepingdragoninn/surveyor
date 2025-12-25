package folk.sisby.surveyor;

import com.google.common.collect.Multimap;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.structure.WorldStructureSummary;
import folk.sisby.surveyor.terrain.WorldTerrainSummary;
import folk.sisby.surveyor.util.MapUtil;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.Structure;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SurveyorEvents {
	private static final Map<Identifier, WorldLoad> worldLoad = new HashMap<>();
	private static final Map<Identifier, TerrainUpdated> terrainUpdated = new HashMap<>();
	private static final Map<Identifier, StructuresAdded> structuresAdded = new HashMap<>();
	private static final Map<Identifier, LandmarksAdded> landmarksAdded = new HashMap<>();
	private static final Map<Identifier, LandmarksRemoved> landmarksRemoved = new HashMap<>();

	@FunctionalInterface
	public interface WorldLoad {
		void onWorldLoad(WorldSummary summary);
	}

	@FunctionalInterface
	public interface TerrainUpdated {
		void onTerrainUpdated(WorldSummary summary, WorldTerrainSummary worldTerrain, Collection<ChunkPos> chunks);
	}

	@FunctionalInterface
	public interface StructuresAdded {
		void onStructuresAdded(WorldSummary summary, WorldStructureSummary worldStructures, Multimap<RegistryKey<Structure>, ChunkPos> structures);
	}

	@FunctionalInterface
	public interface LandmarksAdded {
		void onLandmarksAdded(WorldSummary summary, WorldLandmarks worldLandmarks, Multimap<UUID, Identifier> landmarks);
	}

	@FunctionalInterface
	public interface LandmarksRemoved {
		void onLandmarksRemoved(WorldSummary summary, WorldLandmarks worldLandmarks, Multimap<UUID, Identifier> landmarks);
	}

	public static class Invoke {
		public static void worldLoad(ServerWorld world) {
			if (worldLoad.isEmpty()) return;
			WorldSummary summary = WorldSummary.of(world);
			worldLoad.forEach((id, handler) -> handler.onWorldLoad(world, summary));
		}

		public static void terrainUpdated(WorldSummary summary, Collection<ChunkPos> chunks) {
			if (terrainUpdated.isEmpty() || chunks.isEmpty()) return;
			terrainUpdated.forEach((id, handler) -> handler.onTerrainUpdated(world, summary, summary.terrain(), chunks));
		}

		public static void terrainUpdated(WorldSummary summary, ChunkPos pos) {
			terrainUpdated(world, summary, List.of(pos));
		}

		public static void structuresAdded(WorldSummary summary, Multimap<RegistryKey<Structure>, ChunkPos> structures) {
			if (structuresAdded.isEmpty() || structures.isEmpty()) return;
			structuresAdded.forEach((id, handler) -> handler.onStructuresAdded(world, summary, summary.structures(), structures));
		}

		public static void structuresAdded(WorldSummary summary, RegistryKey<Structure> key, ChunkPos pos) {
			structuresAdded(world, summary, MapUtil.asMultiMap(Map.of(key, List.of(pos))));
		}

		public static void landmarksAdded(WorldSummary summary, Multimap<UUID, Identifier> landmarks) {
			if (landmarksAdded.isEmpty() || landmarks.isEmpty()) return;
			landmarksAdded.forEach((id, handler) -> handler.onLandmarksAdded(world, summary, summary.landmarks(), landmarks));
		}

		public static void landmarksRemoved(WorldSummary summary, Multimap<UUID, Identifier> landmarks) {
			if (landmarksRemoved.isEmpty() || landmarks.isEmpty()) return;
			landmarksRemoved.forEach((id, handler) -> handler.onLandmarksRemoved(world, summary, summary.landmarks(), landmarks));
		}
	}

	public static class Register {
		public static void worldLoad(Identifier id, WorldLoad handler) {
			worldLoad.put(id, handler);
		}

		public static void terrainUpdated(Identifier id, TerrainUpdated handler) {
			terrainUpdated.put(id, handler);
		}

		public static void structuresAdded(Identifier id, StructuresAdded handler) {
			structuresAdded.put(id, handler);
		}

		public static void landmarksAdded(Identifier id, LandmarksAdded handler) {
			landmarksAdded.put(id, handler);
		}

		public static void landmarksRemoved(Identifier id, LandmarksRemoved handler) {
			landmarksRemoved.put(id, handler);
		}
	}
}
