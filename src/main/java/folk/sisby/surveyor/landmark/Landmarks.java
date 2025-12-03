package folk.sisby.surveyor.landmark;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.client.SurveyorClient;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.util.DispatchMapCodec;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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

	public static final int[] XAERO_COLORS = new int[]{
		0xFF_000000, 0xFF_0000AA, 0xFF_00AA00, 0xFF_00AAAA, 0xFF_AA0000, 0xFF_AA00AA, 0xFF_ffAA00, 0xFF_AAAAAA, 0xFF_555555, 0xFF_5555FF, 0xFF_55FF55, 0xFF_55FFFF, 0xFF_FF0000, 0xFF_FF55FF, 0xFF_FFFF55, 0xFF_FFFFFF
	};

	public static Map<UUID, Map<Identifier, Landmark>> fromNbt(NbtCompound nbt, File landmarksFile, File xaerosSaveFolder, Runnable dirty) {
		NbtCompound landmarks = nbt.getCompound(KEY_LANDMARKS);
		Map<UUID, Map<Identifier, Landmark>> outMap = new HashMap<>();
		if (xaerosSaveFolder != null) {
			Surveyor.LOGGER.info("[Surveyor] Attempting to parse xaero's waypoints from {}/{}", xaerosSaveFolder.getParentFile().getName(), xaerosSaveFolder.getName());
			try {
				Files.createFile(xaerosSaveFolder.toPath().resolve(".surveyor_migrated"));
				for (File file : Objects.requireNonNullElse(xaerosSaveFolder.listFiles(f -> f.getName().endsWith(".txt")), new File[0])) {
					for (String line : Files.readAllLines(file.toPath())) {
						try {
							if (line.startsWith("waypoint:")) {
								String[] split = line.split(":");
								BlockPos pos = new BlockPos(Integer.parseInt(split[3]), split[4].equals("~") ? 0 : Integer.parseInt(split[4]), Integer.parseInt(split[5]));
								Identifier id = Identifier.of("xaeros", "waypoint/%d/%d/%d".formatted(pos.getX(), pos.getY(), pos.getZ()));
								outMap.computeIfAbsent(SurveyorClient.getClientUuid(), u -> new HashMap<>()).put(id, Landmark.create(SurveyorClient.getClientUuid(), id, b -> b
									.add(LandmarkComponentTypes.POS, pos)
									.add(LandmarkComponentTypes.COLOR, XAERO_COLORS[Integer.parseInt(split[6])])
									.add(LandmarkComponentTypes.NAME, Text.literal(split[1]))
								));
								dirty.run();
							}
						} catch (Exception e) {
							Surveyor.LOGGER.error("[Surveyor] Error parsing xaeros waypoint: {}", line, e);
						}
					}
				}
			} catch (Exception e) {
				Surveyor.LOGGER.error("[Surveyor] Error parsing xaeros data from {}", xaerosSaveFolder, e);
			}
			Surveyor.LOGGER.info("[Surveyor] Migrated {} waypoints from xaeros data.", outMap.values().stream().mapToInt(Map::size).sum());
		}
		if (landmarks.getKeys().stream().anyMatch(k -> k.contains(":"))) { // 0.X
			Surveyor.LOGGER.warn("[Surveyor] Partially recovering landmarks from 0.X");
			try {
				Files.copy(landmarksFile.toPath(), landmarksFile.toPath().resolveSibling("landmarks.dat_v0"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				throw new RuntimeException("Surveyor failed to back up v0 landmarks, was the file locked?", e);
			}
			try {
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
							.add(LandmarkComponentTypes.NAME, !landmark.contains("name") ? null : TextCodecs.CODEC.decode(NbtOps.INSTANCE, landmark.get("name")).resultOrPartial(Surveyor.LOGGER::error).map(Pair::getFirst).orElse(null))
							.add(LandmarkComponentTypes.SEED, !landmark.contains("seed") ? null : landmark.getInt("seed"))
							.add(LandmarkComponentTypes.TIME, !landmark.contains("created") ? null : landmark.getLong("created"))
						));
						dirty.run();
					}
				}
				Surveyor.LOGGER.info("[Surveyor] Recovered {} landmarks from legacy data.", outMap.values().stream().mapToInt(Map::size).sum());
			} catch (Exception e) {
				Surveyor.LOGGER.error("[Surveyor] Encountered an error during v0 landmark migration, skipping...", e);
			}
		} else {
			CODEC.decode(NbtOps.INSTANCE, landmarks).resultOrPartial(Surveyor.LOGGER::error).orElseThrow().getFirst().forEach((uuid, map) -> map.forEach((id, landmark) -> outMap.computeIfAbsent(uuid, u -> new HashMap<>()).put(id, landmark)));
		}
		return outMap;
	}
}
