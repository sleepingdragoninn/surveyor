package folk.sisby.surveyor.landmark;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import folk.sisby.surveyor.landmark.component.LandmarkComponentMap;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record Landmark(UUID owner, Identifier id, LandmarkComponentMap components) {
	public static Codec<Landmark> createCodec(UUID uuid, Identifier id) {
		return RecordCodecBuilder.create(instance -> instance.group(
			LandmarkComponentMap.CODEC.fieldOf("components").forGetter(Landmark::components)
		).apply(instance, (components) -> new Landmark(uuid, id, components)));
	}
}
