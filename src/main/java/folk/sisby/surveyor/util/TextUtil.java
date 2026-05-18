package folk.sisby.surveyor.util;

import net.minecraft.text.*;
import net.minecraft.util.Formatting;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

public class TextUtil {
	public static Text stripInteraction(Text text) {
		TextContent content = text.getContent() instanceof TranslatableTextContent t
			? new TranslatableTextContent(t.getKey(), t.getFallback(), Arrays.stream(t.getArgs())
																	   .map(a -> a instanceof Text c ? stripInteraction(c) : a)
																	   .toArray())
			: text.getContent();

		MutableText result = MutableText.of(content)
			.setStyle(text.getStyle().withHoverEvent(null).withClickEvent(null).withInsertion(null));
		text.getSiblings().forEach(s -> result.append(stripInteraction(s)));
		return result;
	}

	public static MutableText highlightStrings(Collection<String> list, Function<String, Formatting> highlighter) {
		return Text.literal("[").append(Texts.join(
			list,
			Text.literal(", "),
			s -> Text.literal(s).setStyle(Style.EMPTY.withFormatting(Objects.requireNonNullElse(highlighter.apply(s), Formatting.RESET)))
		)).append(Text.literal("]"));
	}
}
