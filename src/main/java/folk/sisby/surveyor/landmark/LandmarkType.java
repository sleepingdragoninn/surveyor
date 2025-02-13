package folk.sisby.surveyor.landmark;

import com.mojang.serialization.Codec;
import net.minecraft.util.Identifier;

import java.util.UUID;

public interface LandmarkType<T> {
	Identifier id();

	Codec<T> createCodec(UUID uuid, Identifier id);
}
