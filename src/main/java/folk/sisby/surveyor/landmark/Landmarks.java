package folk.sisby.surveyor.landmark;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.util.DispatchMapCodec;
import folk.sisby.surveyor.util.SurveyorCodecs;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class Landmarks {
	public static final String KEY_LANDMARKS = "landmarks";
	private static final Map<Identifier, LandmarkType<?>> TYPES = new HashMap<>();
	public static final Codec<LandmarkType<?>> TYPE_CODEC = Identifier.CODEC.comapFlatMap(Landmarks::decode, LandmarkType::id);
	public static final Codec<Map<UUID, Map<Identifier, Landmark>>> CODEC = DispatchMapCodec.of(
		TYPE_CODEC,
		Landmarks::typedCodec
	);

	static {
		register(SimplePointLandmark.TYPE);
		register(SimplePointOfInterestLandmark.TYPE);
		register(NetherPortalLandmark.TYPE);
		register(PlayerDeathLandmark.TYPE);
	}

	@SuppressWarnings("unchecked")
	public static Codec<Map<Identifier, Landmark>> typedCodec(LandmarkType<T> type) {
		return DispatchMapCodec.of(
			SurveyorCodecs.STRINGIFIED_BLOCKPOS,
			pos -> (Codec<Landmark>) type.createCodec(pos)
		);
	}

	private static DataResult<? extends LandmarkType<?>> decode(Identifier id) {
		return Optional.ofNullable(TYPES.get(id))
			.map(DataResult::success)
			.orElse(DataResult.error(() -> "No landmark type found with id " + id));
	}

	public static boolean containsType(Identifier id) {
		return TYPES.containsKey(id);
	}

	public static NbtCompound writeNbt(Map<UUID, Map<Identifier, Landmark>> landmarks, NbtCompound nbt) {
		nbt.put(KEY_LANDMARKS, CODEC.encodeStart(NbtOps.INSTANCE, landmarks).getOrThrow(false, Surveyor.LOGGER::error));
		return nbt;
	}

	public static Map<UUID, Map<Identifier, Landmark>> fromNbt(NbtCompound nbt) {
		return CODEC.decode(NbtOps.INSTANCE, nbt.getCompound(KEY_LANDMARKS)).getOrThrow(false, Surveyor.LOGGER::error).getFirst();
	}

	public static LandmarkType<?> getType(Identifier id) {
		return TYPES.get(id);
	}

	public static Set<Identifier> keySet() {
		return new HashSet<>(TYPES.keySet());
	}

	public static void register(LandmarkType<?> type) {
		if (containsType(type.id())) {
			throw new IllegalArgumentException("Multiple landmark types registered to the same ID: %s".formatted(type.id()));
		}
		TYPES.put(type.id(), type);
	}
}
