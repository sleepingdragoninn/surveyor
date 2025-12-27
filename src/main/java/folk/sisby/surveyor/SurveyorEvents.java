package folk.sisby.surveyor;

import com.google.common.collect.Multimap;
import folk.sisby.surveyor.util.MapUtil;
import folk.sisby.surveyor.util.RegionPos;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.Structure;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SurveyorEvents {
	private static final Map<Identifier, TerrainUpdated> terrainUpdated = new HashMap<>();
	private static final Map<Identifier, StructuresAdded> structuresAdded = new HashMap<>();
	private static final Map<Identifier, LandmarksAdded> landmarksAdded = new HashMap<>();
	private static final Map<Identifier, LandmarksRemoved> landmarksRemoved = new HashMap<>();

	@FunctionalInterface
	public interface TerrainUpdated {
		void onTerrainUpdated(WorldSummary summary, Map<RegionPos, BitSet> chunks);
	}

	@FunctionalInterface
	public interface StructuresAdded {
		void onStructuresAdded(WorldSummary summary, Multimap<RegistryKey<Structure>, ChunkPos> starts);
	}

	@FunctionalInterface
	public interface LandmarksAdded {
		void onLandmarksAdded(WorldSummary summary, Multimap<UUID, Identifier> landmarks);
	}

	@FunctionalInterface
	public interface LandmarksRemoved {
		void onLandmarksRemoved(WorldSummary summary, Multimap<UUID, Identifier> landmarks);
	}

	public static class Invoke {
		public static void terrainUpdated(WorldSummary summary, Map<RegionPos, BitSet> chunks) {
			if (terrainUpdated.isEmpty() || chunks.isEmpty()) return;
			terrainUpdated.forEach((id, handler) -> handler.onTerrainUpdated(summary, chunks));
		}

		public static void terrainUpdated(WorldSummary summary, ChunkPos pos) {
			terrainUpdated(summary, Map.of(RegionPos.of(pos), RegionPos.chunkToBitSet(pos)));
		}

		public static void structuresAdded(WorldSummary summary, Multimap<RegistryKey<Structure>, ChunkPos> starts) {
			if (structuresAdded.isEmpty() || starts.isEmpty()) return;
			structuresAdded.forEach((id, handler) -> handler.onStructuresAdded(summary, starts));
		}

		public static void structuresAdded(WorldSummary summary, RegistryKey<Structure> key, ChunkPos pos) {
			structuresAdded(summary, MapUtil.asMultiMap(Map.of(key, List.of(pos))));
		}

		public static void landmarksAdded(WorldSummary summary, Multimap<UUID, Identifier> landmarks) {
			if (landmarksAdded.isEmpty() || landmarks.isEmpty()) return;
			landmarksAdded.forEach((id, handler) -> handler.onLandmarksAdded(summary, landmarks));
		}

		public static void landmarksRemoved(WorldSummary summary, Multimap<UUID, Identifier> landmarks) {
			if (landmarksRemoved.isEmpty() || landmarks.isEmpty()) return;
			landmarksRemoved.forEach((id, handler) -> handler.onLandmarksRemoved(summary, landmarks));
		}
	}

	public static class Register {
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
