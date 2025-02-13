package folk.sisby.surveyor.landmark;

import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public interface VariableLandmark<T extends VariableLandmark> extends Landmark {
	default @Nullable UUID owner() {
		return optionalOwner().orElse(null);
	}

	Optional<UUID> optionalOwner();

	default @Nullable DyeColor color() {
		return optionalColor().orElse(null);
	}

	Optional<DyeColor> optionalColor();

	default @Nullable Text name() {
		return optionalName().orElse(null);
	}

	Optional<Text> optionalName();

	default @Nullable Text description() {
		return optionalDescription().orElse(null);
	}

	Optional<Text> optionalDescription();

	default @Nullable Identifier texture() {
		return optionalTexture().orElse(null);
	}

	Optional<Identifier> optionalTexture();
}
