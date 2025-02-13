package folk.sisby.surveyor.landmark;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface HasAxisBlockBoxMergeable extends HasAxis, HasBlockBox {
	private static Map<UUID, Map<Identifier, Landmark>> tryMergeOnce(Map<UUID, Map<Identifier, Landmark>> changed, World world, WorldLandmarks landmarks) {
		Map<UUID, Map<Identifier, Landmark>> mergeableLandmarks = landmarks.asMap(null);

		for (Map<Identifier, Landmark> posMap : mergeableLandmarks.values()) {
			for (Landmark genericLandmark : posMap.values()) {
				if (!(genericLandmark instanceof HasAxisBlockBoxMergeable landmark)) break;
				for (Landmark genericLandmark2 : posMap.values()) {
					if (!(genericLandmark2 instanceof HasAxisBlockBoxMergeable landmark2)) break;
					if (genericLandmark == genericLandmark2) continue;
					if (landmark.axis().equals(landmark2.axis())) {
						BlockBox joined = BlockBox.encompass(List.of(landmark.box(), landmark2.box())).orElseThrow();
						if (joined.getBlockCountX() * joined.getBlockCountY() * joined.getBlockCountZ() == landmark.box().getBlockCountX() * landmark.box().getBlockCountY() * landmark.box().getBlockCountZ() + landmark2.box().getBlockCountX() * landmark2.box().getBlockCountY() * landmark2.box().getBlockCountZ()) {
							genericLandmark.remove(changed, world, landmarks);
							genericLandmark2.remove(changed, world, landmarks);
							landmark.box().encompass(landmark2.box());
							genericLandmark.put(changed, world, landmarks);
							return changed;
						}
					}
				}
			}
		}
		return changed;
	}

	default Map<UUID, Map<Identifier, Landmark>> tryMerge(Map<UUID, Map<Identifier, Landmark>> changed, World world, WorldLandmarks landmarks) {
		int oldSize;
		int newSize;
		do {
			oldSize = changed.values().stream().mapToInt(Map::size).sum();
			tryMergeOnce(changed, world, landmarks);
			newSize = changed.values().stream().mapToInt(Map::size).sum();
		} while (newSize > oldSize);
		return changed;
	}
}
