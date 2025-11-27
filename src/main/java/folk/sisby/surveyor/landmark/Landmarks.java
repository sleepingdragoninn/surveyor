package folk.sisby.surveyor.landmark;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.util.DispatchMapCodec;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Landmarks {
	public static final String KEY_LANDMARKS = "landmarks";
	public static final Codec<Map<UUID, Map<Identifier, Landmark>>> CODEC = DispatchMapCodec.of(
		Uuids.STRING_CODEC,
		uuid -> DispatchMapCodec.of(
			Identifier.CODEC,
			id -> Landmark.createCodec(uuid, id)
		)
	);

	public static NbtCompound writeNbt(Map<UUID, Map<Identifier, Landmark>> landmarks, NbtCompound nbt) {
		nbt.put(KEY_LANDMARKS, CODEC.encodeStart(NbtOps.INSTANCE, landmarks).resultOrPartial(Surveyor.LOGGER::error).orElseThrow());
		return nbt;
	}

	public static Map<UUID, Map<Identifier, Landmark>> fromNbt(NbtCompound nbt, File landmarksFile) {
		NbtCompound landmarks = nbt.getCompound(KEY_LANDMARKS);
		if (landmarks.getKeys().stream().anyMatch(k -> k.contains(":"))) { // 0.X
			Surveyor.LOGGER.warn("[Surveyor] Partially recovering landmarks from 0.X");
			try {
				Files.copy(landmarksFile.toPath(), landmarksFile.toPath().resolveSibling("landmarks.dat_v0"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				throw new RuntimeException("Surveyor failed to back up v0 landmarks, was the file locked?", e);
			}
			try {
				Map<UUID, Map<Identifier, Landmark>> outMap = new HashMap<>();
				for (String key : landmarks.getKeys()) {
					NbtCompound type = landmarks.getCompound(key);
					Identifier typeId = Identifier.tryParse(key);
					for (String coords : type.getKeys()) {
						NbtCompound landmark = type.getCompound(coords);
						UUID owner = landmark.contains("owner") ? Uuids.CODEC.decode(NbtOps.INSTANCE, landmark.get("owner")).resultOrPartial(Surveyor.LOGGER::error).orElseThrow().getFirst() : WorldLandmarks.GLOBAL;
						BlockPos pos = new BlockPos(Integer.parseInt(coords.split(",")[0]), Integer.parseInt(coords.split(",")[1]), Integer.parseInt(coords.split(",")[2]));
						DyeColor dye = !landmark.contains("color") ? null : DyeColor.CODEC.decode(NbtOps.INSTANCE, landmark.get("color")).resultOrPartial(Surveyor.LOGGER::error).map(Pair::getFirst).orElse(null);
						Identifier id = (landmark.contains("texture") ? Identifier.tryParse(landmark.getString("texture")) : typeId).withSuffixedPath((dye == null ? "" : "/" + dye.getName()) + "/" + pos.getX() + (pos.getY() == 0 ? "" : "/" + pos.getY()) + "/" + pos.getZ());
						outMap.computeIfAbsent(owner, u -> new HashMap<>()).put(id, Landmark.create(owner, id, b -> b
							.add(LandmarkComponentTypes.POS, pos)
							.add(LandmarkComponentTypes.BOX, !landmark.contains("box") ? null : BlockBox.CODEC.decode(NbtOps.INSTANCE, landmark.get("box")).resultOrPartial(Surveyor.LOGGER::error).map(Pair::getFirst).orElse(null))
							.add(LandmarkComponentTypes.COLOR, dye == null ? null : dye.getFireworkColor())
							.add(LandmarkComponentTypes.NAME, !landmark.contains("name") ? null : Codecs.TEXT.decode(NbtOps.INSTANCE, landmark.get("name")).resultOrPartial(Surveyor.LOGGER::error).map(Pair::getFirst).orElse(null))
							.add(LandmarkComponentTypes.SEED, !landmark.contains("seed") ? null : landmark.getInt("seed"))
							.add(LandmarkComponentTypes.TIME, !landmark.contains("created") ? null : landmark.getLong("created"))
						));
					}
				}
				Surveyor.LOGGER.info("[Surveyor] Recovered {} landmarks from legacy data.", outMap.values().stream().mapToInt(Map::size).sum());
				return outMap;
			} catch (Exception e) {
				Surveyor.LOGGER.error("[Surveyor] Encountered an error during v0 landmark migration, skipping...", e);
				return new HashMap<>();
			}
		}
		return CODEC.decode(NbtOps.INSTANCE, landmarks).resultOrPartial(Surveyor.LOGGER::error).orElseThrow().getFirst();
	}
}
