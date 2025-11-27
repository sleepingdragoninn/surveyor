package folk.sisby.surveyor.landmark.component;

import com.mojang.serialization.Codec;
import folk.sisby.surveyor.Surveyor;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;

import java.util.List;
import java.util.function.Function;

public class LandmarkComponentTypes {
	public static final LandmarkComponentType<BlockPos> POS = register("pos", BlockPos.CODEC, p -> Text.literal("[").append(Text.literal(p.toShortString()).formatted(Formatting.GOLD)).append(Text.literal("]")));
	public static final LandmarkComponentType<Text> NAME = register("name", Codecs.TEXT, t -> Text.literal("\"").append(t.copy().formatted(Formatting.GREEN)).append(Text.literal("\"")));
	public static final LandmarkComponentType<List<Text>> LORE = register("lore", Codec.list(Codecs.TEXT), l -> Texts.join(l, Text.literal("\" | \""), t -> t.copy().formatted(Formatting.GREEN)));
	public static final LandmarkComponentType<Integer> COLOR = register("color", Codec.INT, i -> Text.literal("#").setStyle(Style.EMPTY.withColor(i)).append(Text.literal(Integer.toHexString(i).toUpperCase()).formatted(Formatting.GOLD)));
	public static final LandmarkComponentType<Long> TIME = register("time", Codec.LONG, tick -> Text.of("Day %d, %d:%02d".formatted(1 + (tick / 24000), ((6000 + tick) % 24000) / 1000, tick % 1000 > 500 ? 30 : 0)));
	public static final LandmarkComponentType<Integer> SEED = register("seed", Codec.INT, i -> Text.literal(String.valueOf(i)).formatted(Formatting.GOLD));
	public static final LandmarkComponentType<BlockBox> BOX = register("box", BlockBox.CODEC, b -> Text.literal("[").append(Text.literal(new BlockPos(b.getMinX(), b.getMinY(), b.getMinZ()).toShortString()).formatted(Formatting.GOLD)).append(Text.literal("]->[")).append(Text.literal(new BlockPos(b.getMaxX(), b.getMaxY(), b.getMaxZ()).toShortString()).formatted(Formatting.GOLD)).append(Text.literal("]")));
	public static final LandmarkComponentType<ItemStack> STACK = register("stack", ItemStack.CODEC, s -> Text.literal("").append(Text.literal("[")).append(s.getItem().getName().copy().formatted(s.getRarity().formatting)).append(Text.literal("]")).append(s.hasCustomName() ? Text.literal(" - \"").append(s.getName().copy().formatted(Formatting.GREEN)).append(Text.literal("\"")) : Text.literal("")));

	public static LandmarkComponentMap.Builder forBlock(LandmarkComponentMap.Builder builder, WorldAccess world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		ItemStack stack = state.getBlock().getPickStack(world, pos, world.getBlockState(pos));
		BlockEntity entity = world.getBlockEntity(pos);
		if (entity != null && Registries.BLOCK_ENTITY_TYPE.getKey(entity.getType()).map(t -> Surveyor.CONFIG.builtins.allowedBlockEntities.contains(t.toString())).orElse(false)) {
			BlockItem.setBlockEntityNbt(stack, entity.getType(), entity.createNbt());
		}
		builder.add(NAME, !stack.isEmpty() ? stack.getName() : state.getBlock().getName());
		if (!stack.isEmpty()) builder.add(STACK, stack);
		int color = state.getMapColor(world, pos).getRenderColor(MapColor.Brightness.HIGH);
		if (color != 0) builder.add(COLOR, color);
		builder.add(POS, pos);
		return builder;
	}

	private static <T> LandmarkComponentType<T> register(String path, Codec<T> codec, Function<T, Text> viewer) {
		return register(Surveyor.id(path), codec, viewer);
	}

	public static <T> LandmarkComponentType<T> register(Identifier id, Codec<T> codec, Function<T, Text> viewer) {
		LandmarkComponentType<T> type = new LandmarkComponentType<>(id, codec, viewer);
		LandmarkComponentType.register(type);
		return type;
	}

	public static void touch() {
	}
}
