package folk.sisby.surveyor.landmark.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public record LandmarkComponentType<T>(Identifier id, Codec<T> codec, Function<T, Text> viewer) {
	private static final Map<Identifier, LandmarkComponentType<?>> TYPES = new HashMap<>();
	public static final Codec<LandmarkComponentType<?>> CODEC = Identifier.CODEC.comapFlatMap(LandmarkComponentType::decode, LandmarkComponentType::id);

	private static DataResult<? extends LandmarkComponentType<?>> decode(Identifier id) {
		return Optional.ofNullable(TYPES.get(id))
			.map(DataResult::success)
			.orElse(DataResult.error(() -> "No landmark component type found with id " + id));
	}

	public static boolean containsType(Identifier id) {
		return TYPES.containsKey(id);
	}

	public static LandmarkComponentType<?> getType(Identifier id) {
		return TYPES.get(id);
	}

	public static Set<Identifier> keySet() {
		return new HashSet<>(TYPES.keySet());
	}

	public static void register(LandmarkComponentType<?> type) {
		if (containsType(type.id())) {
			throw new IllegalArgumentException("Multiple landmark types registered to the same ID: %s".formatted(type.id()));
		}
		TYPES.put(type.id(), type);
	}
}
