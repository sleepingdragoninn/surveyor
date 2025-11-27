package folk.sisby.surveyor.util;

import folk.sisby.surveyor.Surveyor;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.Formatting;
import net.minecraft.util.dynamic.Codecs;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class TextUtil {
	public static Text stripInteraction(Text text) {
		try {
			NbtCompound nbt = (NbtCompound) Codecs.TEXT.encodeStart(NbtOps.INSTANCE, text).resultOrPartial(Surveyor.LOGGER::error).orElseThrow();
			NbtUtil.removeRecursive(nbt, List.of("hoverEvent", "clickEvent", "insertion"));
			return Codecs.TEXT.decode(NbtOps.INSTANCE, nbt).resultOrPartial(Surveyor.LOGGER::error).orElseThrow().getFirst();
		} catch (Exception e) {
			return Text.literal(text.getString());
		}
	}

	public static MutableText highlightStrings(Collection<String> list, Function<String, Formatting> highlighter) {
		return Text.literal("[").append(Texts.join(
			list,
			Text.literal(", "),
			s -> Text.literal(s).setStyle(Style.EMPTY.withFormatting(Objects.requireNonNullElse(highlighter.apply(s), Formatting.RESET)))
		)).append(Text.literal("]"));
	}
}
