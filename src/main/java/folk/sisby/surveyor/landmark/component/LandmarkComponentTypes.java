package folk.sisby.surveyor.landmark.component;

import com.mojang.serialization.Codec;
import folk.sisby.surveyor.Surveyor;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.List;

public class LandmarkComponentTypes {
	public static final LandmarkComponentType<BlockPos> POS = register("pos", BlockPos.CODEC);
	public static final LandmarkComponentType<Text> NAME = register("name", TextCodecs.CODEC);
	public static final LandmarkComponentType<List<Text>> LORE = register("lore", Codec.list(TextCodecs.CODEC));
	public static final LandmarkComponentType<Integer> COLOR = register("color", Codec.INT);
	public static final LandmarkComponentType<Long> TIME = register("time", Codec.LONG);
	public static final LandmarkComponentType<Integer> SEED = register("seed", Codec.INT);
	public static final LandmarkComponentType<BlockBox> BOX = register("box", BlockBox.CODEC);
	public static final LandmarkComponentType<Direction.Axis> AXIS = register("axis", Direction.Axis.CODEC);

	private static <T> LandmarkComponentType<T> register(String path, Codec<T> codec) {
		return register(Surveyor.id(path), codec);
	}

	public static <T> LandmarkComponentType<T> register(Identifier id, Codec<T> codec) {
		LandmarkComponentType<T> type = new LandmarkComponentType<>(id, codec);
		LandmarkComponentType.register(type);
		return type;
	}
}
