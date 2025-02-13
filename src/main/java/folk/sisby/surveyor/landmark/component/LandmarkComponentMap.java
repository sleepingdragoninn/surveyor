package folk.sisby.surveyor.landmark.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import folk.sisby.surveyor.util.DispatchMapCodec;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public record LandmarkComponentMap(Reference2ObjectMap<LandmarkComponentType<?>, Object> map) {
	private static final Codec<Map<LandmarkComponentType<?>, Object>> TYPE_TO_VALUE_MAP_CODEC = DispatchMapCodec.of(
		LandmarkComponentType.CODEC,
		type -> (Codec<Object>) type.codec()
	);

	public static final Codec<LandmarkComponentMap> CODEC = createCodecFromValueMap(TYPE_TO_VALUE_MAP_CODEC);

	private static Codec<LandmarkComponentMap> createCodecFromValueMap(Codec<Map<LandmarkComponentType<?>, Object>> typeToValueMapCodec) {
		return typeToValueMapCodec.flatComapMap(LandmarkComponentMap.Builder::build, componentMap -> {
			int i = componentMap.size();
			if (i == 0) {
				return DataResult.success(Reference2ObjectMaps.emptyMap());
			} else {
				Reference2ObjectMap<LandmarkComponentType<?>, Object> reference2ObjectMap = new Reference2ObjectArrayMap<>(i);
				reference2ObjectMap.putAll(componentMap.map());
				return DataResult.success(reference2ObjectMap);
			}
		});
	}

	@SuppressWarnings("unchecked")
	@Nullable
	public <T> T get(LandmarkComponentType<? extends T> type) {
		return (T) this.map.get(type);
	}

	public boolean contains(LandmarkComponentType<?> type) {
		return this.map.containsKey(type);
	}

	public Set<LandmarkComponentType<?>> keySet() {
		return this.map.keySet();
	}

	public int size() {
		return this.map.size();
	}

	public String toString() {
		return this.map.toString();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private final Reference2ObjectMap<LandmarkComponentType<?>, Object> components = new Reference2ObjectArrayMap<>();

		public <T> LandmarkComponentMap.Builder add(LandmarkComponentType<T> type, @Nullable T value) {
			this.put(type, value);
			return this;
		}

		<T> void put(LandmarkComponentType<T> type, @Nullable Object value) {
			if (value != null) {
				this.components.put(type, value);
			} else {
				this.components.remove(type);
			}
		}

		public LandmarkComponentMap build() {
			return build(this.components);
		}

		private static LandmarkComponentMap build(Map<LandmarkComponentType<?>, Object> components) {
			if (components.isEmpty()) {
				return new LandmarkComponentMap(new Reference2ObjectArrayMap<>());
			} else {
				return components.size() < 8
					? new LandmarkComponentMap(new Reference2ObjectArrayMap<>(components))
					: new LandmarkComponentMap(new Reference2ObjectOpenHashMap<>(components));
			}
		}
	}
}
