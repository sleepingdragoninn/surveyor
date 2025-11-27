package folk.sisby.surveyor.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;

public class SurveyorCodecs {
	public static final Codec<NbtElement> NBT_ELEMENT = Codec.PASSTHROUGH
		.comapFlatMap(
			dynamic -> {
				NbtElement nbtElement = dynamic.convert(NbtOps.INSTANCE).getValue();
				return DataResult.success(nbtElement == dynamic.getValue() ? nbtElement.copy() : nbtElement);
			},
			nbt -> new Dynamic<>(NbtOps.INSTANCE, nbt.copy())
		);
}
