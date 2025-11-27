package folk.sisby.surveyor.landmark.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.util.SurveyorCodecs;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public record LandmarkComponentType<T>(Identifier id, Codec<T> codec, Function<T, Text> viewer) {
	private static final Map<Identifier, LandmarkComponentType<?>> TYPES = new HashMap<>();
	public static final Codec<LandmarkComponentType<?>> CODEC = Identifier.CODEC.comapFlatMap(LandmarkComponentType::decode, LandmarkComponentType::id);

	private static DataResult<? extends LandmarkComponentType<?>> decode(Identifier id) {
		LandmarkComponentType<?> type = TYPES.get(id);
		if (type != null) return DataResult.success(type);
		Surveyor.LOGGER.warn("[Surveyor] Generating passthrough for unregistered component type {}", id);
		return DataResult.success(LandmarkComponentTypes.register(id, SurveyorCodecs.NBT_ELEMENT, NbtHelper::toPrettyPrintedText));
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
