package folk.sisby.surveyor.landmark;

import com.mojang.serialization.Codec;
import folk.sisby.surveyor.util.DispatchMapCodec;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.Map;
import java.util.UUID;

public class Landmarks {
	public static final String KEY_LANDMARKS = "landmarks";
	public static final Codec<Map<UUID, Map<Identifier, Landmark>>> CODEC = DispatchMapCodec.of(
		Uuids.STRING_CODEC,
		uuid -> DispatchMapCodec.of(
			Identifier.CODEC,
			id -> Landmark.createCodec(uuid, id)
		)
	);

	public static NbtCompound writeNbt(Map<UUID, Map<Identifier, Landmark>> landmarks, NbtCompound nbt) {
		nbt.put(KEY_LANDMARKS, CODEC.encodeStart(NbtOps.INSTANCE, landmarks).getOrThrow());
		return nbt;
	}

	public static Map<UUID, Map<Identifier, Landmark>> fromNbt(NbtCompound nbt) {
		return CODEC.decode(NbtOps.INSTANCE, nbt.getCompound(KEY_LANDMARKS).orElse(new NbtCompound())).getOrThrow().getFirst();
	}
}
