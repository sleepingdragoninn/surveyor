package folk.sisby.surveyor.util;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MapUtil {
	public static <K, V> Multimap<K, V> asMultiMap(Map<K, ? extends Collection<V>> asMap) {
		Multimap<K, V> map = HashMultimap.create();
		asMap.forEach(map::putAll);
		return map;
	}

	public static <K, V> Multimap<K, V> keyMultiMap(Map<K, ? extends Map<V, ?>> asMap) {
		Multimap<K, V> map = HashMultimap.create();
		asMap.forEach((key, innerMap) -> map.putAll(key, new HashSet<>(innerMap.keySet())));
		return map;
	}

	public static <K, V> Map<K, List<V>> asListMap(Multimap<K, V> multimap) {
		return multimap.asMap().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> new ArrayList<>(e.getValue())));
	}

	public static <K1, K2, V> Map<K1, Map<K2, V>> splitByKeyMap(Map<K1, Map<K2, V>> map, Multimap<K1, K2> keySet) {
		Map<K1, Map<K2, V>> outMap = new HashMap<>();
		keySet.forEach((k1, k2) -> outMap.computeIfAbsent(k1, k -> new HashMap<>()).put(k2, map.get(k1).get(k2)));
		return outMap;
	}

	public static <K, V> Map<K, V> splitByKeySet(Map<K, V> map, Collection<K> keySet) {
		Map<K, V> outMap = new HashMap<>();
		keySet.forEach(k -> {
			if (map.containsKey(k)) outMap.put(k, map.get(k));
		});
		return outMap;
	}
}
