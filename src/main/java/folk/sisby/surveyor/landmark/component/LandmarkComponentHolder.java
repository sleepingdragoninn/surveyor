package folk.sisby.surveyor.landmark.component;

import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

public interface LandmarkComponentHolder {
	LandmarkComponentMap components();

	@Nullable
	default <T> T get(LandmarkComponentType<? extends T> type) {
		return this.components().get(type);
	}

	@Nullable
	default <T> Text getView(LandmarkComponentType<T> type) {
		return type.viewer().apply(this.components().get(type));
	}

	default <T> T getOrDefault(LandmarkComponentType<? extends T> type, T fallback) {
		return this.components().getOrDefault(type, fallback);
	}

	@Nullable
	default <T> T set(LandmarkComponentType<T> type, @Nullable T value) {
		return this.components().set(type, value);
	}

	@Nullable
	default <T, U> T apply(LandmarkComponentType<T> type, T defaultValue, U change, BiFunction<T, U, T> applier) {
		return this.set(type, applier.apply(this.getOrDefault(type, defaultValue), change));
	}

	@Nullable
	default <T> T apply(LandmarkComponentType<T> type, T defaultValue, UnaryOperator<T> applier) {
		T object = this.getOrDefault(type, defaultValue);
		return this.set(type, applier.apply(object));
	}

	@Nullable
	default <T> T remove(LandmarkComponentType<? extends T> type) {
		return this.components().remove(type);
	}

	default boolean contains(LandmarkComponentType<?> type) {
		return this.components().contains(type);
	}
}
