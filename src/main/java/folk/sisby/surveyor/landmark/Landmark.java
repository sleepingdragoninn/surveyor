package folk.sisby.surveyor.landmark;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import folk.sisby.surveyor.landmark.component.LandmarkComponentHolder;
import folk.sisby.surveyor.landmark.component.LandmarkComponentMap;
import net.minecraft.util.Identifier;

import java.util.UUID;
import java.util.function.UnaryOperator;

public record Landmark(UUID owner, Identifier id, LandmarkComponentMap components) implements LandmarkComponentHolder {
	public static Codec<Landmark> createCodec(UUID uuid, Identifier id) {
		return RecordCodecBuilder.create(instance -> instance.group(
			LandmarkComponentMap.CODEC.fieldOf("components").forGetter(Landmark::components)
		).apply(instance, (components) -> new Landmark(uuid, id, components)));
	}

	public static Landmark create(UUID owner, Identifier id, UnaryOperator<LandmarkComponentMap.Builder> componentChanges) {
		return new Landmark(owner, id, componentChanges.apply(LandmarkComponentMap.builder()).build());
	}

	public static Landmark global(Identifier id, UnaryOperator<LandmarkComponentMap.Builder> componentChanges) {
		return create(WorldLandmarks.GLOBAL, id, componentChanges);
	}

	public static Landmark createIncremental(WorldLandmarks landmarks, UUID uuid, Identifier prefix, UnaryOperator<LandmarkComponentMap.Builder> componentChanges) {
		int i = 1;
		while (landmarks.contains(uuid, Identifier.of(prefix.getNamespace(), prefix.getPath() + "/" + i))) {
			i++;
		}
		return create(uuid, Identifier.of(prefix.getNamespace(), prefix.getPath() + "/" + i), componentChanges);
	}

	public static Landmark globalIncremental(WorldLandmarks landmarks, Identifier prefix, UnaryOperator<LandmarkComponentMap.Builder> componentChanges) {
		return createIncremental(landmarks, WorldLandmarks.GLOBAL, prefix, componentChanges);
	}
}
