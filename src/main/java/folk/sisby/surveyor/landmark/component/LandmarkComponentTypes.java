package folk.sisby.surveyor.landmark.component;

import com.mojang.serialization.Codec;
import folk.sisby.surveyor.Surveyor;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;

import java.util.List;

public class LandmarkComponentTypes {
	public static final LandmarkComponentType<BlockPos> POS = register("pos", BlockPos.CODEC);
	public static final LandmarkComponentType<Text> NAME = register("name", Codecs.TEXT);
	public static final LandmarkComponentType<List<Text>> LORE = register("lore", Codec.list(Codecs.TEXT));
	public static final LandmarkComponentType<Integer> COLOR = register("color", Codec.INT);
	public static final LandmarkComponentType<Long> TIME = register("time", Codec.LONG);
	public static final LandmarkComponentType<Integer> SEED = register("seed", Codec.INT);
	public static final LandmarkComponentType<BlockBox> BOX = register("box", BlockBox.CODEC);
	public static final LandmarkComponentType<Direction.Axis> AXIS = register("axis", Direction.Axis.CODEC);
	public static final LandmarkComponentType<ItemStack> STACK = register("stack", ItemStack.CODEC);

	public static LandmarkComponentMap.Builder forBlock(LandmarkComponentMap.Builder builder, WorldAccess world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		ItemStack stack = state.getBlock().getPickStack(world, pos, world.getBlockState(pos));
		BlockEntity entity = world.getBlockEntity(pos);
		if (entity != null && Registries.BLOCK_ENTITY_TYPE.getKey(entity.getType()).map(t -> Surveyor.CONFIG.builtins.allowedBlockEntities.contains(t.toString())).orElse(false)) {
			BlockItem.setBlockEntityNbt(stack, entity.getType(), entity.createNbt());
		}
		builder.add(NAME, stack.getName());
		builder.add(POS, pos);
		builder.add(STACK, stack);
		return builder;
	}

	private static <T> LandmarkComponentType<T> register(String path, Codec<T> codec) {
		return register(Surveyor.id(path), codec);
	}

	public static <T> LandmarkComponentType<T> register(Identifier id, Codec<T> codec) {
		LandmarkComponentType<T> type = new LandmarkComponentType<>(id, codec);
		LandmarkComponentType.register(type);
		return type;
	}

	public static void touch() {}
}
