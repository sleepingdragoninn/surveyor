package folk.sisby.surveyor.util;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.Formatting;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class TextUtil {
	public static Text stripInteraction(Text text) {
		MutableText mutable = text.copy();
		List<Text> siblings = mutable.getSiblings().stream().map(TextUtil::stripInteraction).toList();
		mutable.getSiblings().clear();
		mutable.getSiblings().addAll(siblings);
		return stripInteractionNonRecursively(mutable);
	}

	public static Text stripInteractionNonRecursively(Text text) {
		return text.copy().styled(s -> s.withHoverEvent(null).withClickEvent(null).withInsertion(null));
	}

	public static MutableText highlightStrings(Collection<String> list, Function<String, Formatting> highlighter) {
		return Text.literal("[").append(Texts.join(
			list,
			Text.literal(", "),
			s -> Text.literal(s).setStyle(Style.EMPTY.withFormatting(Objects.requireNonNullElse(highlighter.apply(s), Formatting.RESET)))
		)).append(Text.literal("]"));
	}
}
