package folk.sisby.surveyor;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import folk.sisby.surveyor.config.SurveyorConfig;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.mixin.AccessServerPlayerEntity;
import folk.sisby.surveyor.terrain.LayerSummary;
import folk.sisby.surveyor.terrain.WorldTerrain;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.item.BannerItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapDecoration;
import net.minecraft.item.map.MapDecorationType;
import net.minecraft.item.map.MapDecorationTypes;
import net.minecraft.item.map.MapState;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.dimension.DimensionType;
import org.apache.commons.lang3.text.WordUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SurveyorMapIntegration {
	public static final TagKey<Block> RECORD_FROM_MAP = TagKey.of(RegistryKeys.BLOCK, Surveyor.id("record_from_map"));
	public static final TagKey<Block> RECORD_TO_MAP = TagKey.of(RegistryKeys.BLOCK, Surveyor.id("record_to_map"));

	public static final Map<RegistryEntry<MapDecorationType>, Item> VANILLA_ICON_STACKS = Map.ofEntries(
		Map.entry(MapDecorationTypes.PLAYER, Items.PLAYER_HEAD),
		Map.entry(MapDecorationTypes.FRAME, Items.ITEM_FRAME),
		Map.entry(MapDecorationTypes.RED_MARKER, Items.RED_DYE),
		Map.entry(MapDecorationTypes.BLUE_MARKER, Items.BLUE_DYE),
		Map.entry(MapDecorationTypes.TARGET_X, Items.NETHER_STAR),
		Map.entry(MapDecorationTypes.TARGET_POINT, Items.REDSTONE),
		Map.entry(MapDecorationTypes.PLAYER_OFF_MAP, Items.PLAYER_HEAD),
		Map.entry(MapDecorationTypes.PLAYER_OFF_LIMITS, Items.PLAYER_HEAD),
		Map.entry(MapDecorationTypes.MANSION, Items.TOTEM_OF_UNDYING),
		Map.entry(MapDecorationTypes.MONUMENT, Items.TRIDENT),
		Map.entry(MapDecorationTypes.BANNER_WHITE, Items.WHITE_BANNER),
		Map.entry(MapDecorationTypes.BANNER_ORANGE, Items.ORANGE_BANNER),
		Map.entry(MapDecorationTypes.BANNER_MAGENTA, Items.MAGENTA_BANNER),
		Map.entry(MapDecorationTypes.BANNER_LIGHT_BLUE, Items.LIGHT_BLUE_BANNER),
		Map.entry(MapDecorationTypes.BANNER_YELLOW, Items.YELLOW_BANNER),
		Map.entry(MapDecorationTypes.BANNER_LIME, Items.LIME_BANNER),
		Map.entry(MapDecorationTypes.BANNER_PINK, Items.PINK_BANNER),
		Map.entry(MapDecorationTypes.BANNER_GRAY, Items.GRAY_BANNER),
		Map.entry(MapDecorationTypes.BANNER_LIGHT_GRAY, Items.LIGHT_GRAY_BANNER),
		Map.entry(MapDecorationTypes.BANNER_CYAN, Items.CYAN_BANNER),
		Map.entry(MapDecorationTypes.BANNER_PURPLE, Items.PURPLE_BANNER),
		Map.entry(MapDecorationTypes.BANNER_BLUE, Items.BLUE_BANNER),
		Map.entry(MapDecorationTypes.BANNER_BROWN, Items.BROWN_BANNER),
		Map.entry(MapDecorationTypes.BANNER_GREEN, Items.GREEN_BANNER),
		Map.entry(MapDecorationTypes.BANNER_RED, Items.RED_BANNER),
		Map.entry(MapDecorationTypes.BANNER_BLACK, Items.BLACK_BANNER),
		Map.entry(MapDecorationTypes.RED_X, Items.CHEST),
		Map.entry(MapDecorationTypes.VILLAGE_DESERT, Items.SANDSTONE),
		Map.entry(MapDecorationTypes.VILLAGE_PLAINS, Items.OAK_LOG),
		Map.entry(MapDecorationTypes.VILLAGE_SAVANNA, Items.ACACIA_LOG),
		Map.entry(MapDecorationTypes.VILLAGE_SNOWY, Items.SNOW_BLOCK),
		Map.entry(MapDecorationTypes.VILLAGE_TAIGA, Items.SPRUCE_LOG),
		Map.entry(MapDecorationTypes.JUNGLE_TEMPLE, Items.MOSSY_COBBLESTONE),
		Map.entry(MapDecorationTypes.SWAMP_HUT, Items.BREWING_STAND),
		Map.entry(MapDecorationTypes.TRIAL_CHAMBERS, Items.TRIAL_SPAWNER)
	);

	public static void applyMapData(ServerPlayerEntity player, MapState mapState) {
		ServerSummary summary = ServerSummary.of(((AccessServerPlayerEntity) player).getServer());
		SurveyorExploration exploration = summary.getSharingExploration(Surveyor.getUuid(player), Surveyor.CONFIG.networking.terrain, true);
		if (Surveyor.CONFIG.builtins.recordToMapItems == SurveyorConfig.Builtins.RecordStyle.COLOR) {
			fillMap(summary, exploration, mapState);
		} else {
			fillExplorationMap(summary, exploration, mapState);
		}
	}

	public static void recordMapData(ServerPlayerEntity player, MapState mapState) {
		Set<ChunkPos> exploredChunks = new HashSet<>();
		Set<Landmark> exploredWaypoints = new HashSet<>();
		int blocksPerPixel = 1 << mapState.scale;
		int originX = mapState.centerX - 64 * blocksPerPixel;
		int originZ = mapState.centerZ - 64 * blocksPerPixel;
		int chunkWidth = blocksPerPixel * 128 / 16;
		SurveyorExploration exploration = SurveyorExploration.of(player);
		WorldTerrain terrain = ServerSummary.of(((AccessServerPlayerEntity) player).getServer()).getWorld(mapState.dimension).terrain();
		boolean explorer = mapState.unlimitedTracking;
		if (terrain != null) {
			for (int x = 0; x < chunkWidth; x++) {
				for (int z = 0; z < chunkWidth; z++) {
					if (explorer || mapState.colors[x * 16 / blocksPerPixel + 128 * z * 16 / blocksPerPixel] != MapColor.CLEAR.id) {
						ChunkPos chunkPos = ChunkPos.fromBlockPos(new BlockPos(originX + x * 16, 0, originZ + z * 16));
						if (!exploration.exploredChunk(mapState.dimension, chunkPos)) {
							exploredChunks.add(chunkPos);
						}
					}
				}
			}
			exploredChunks.forEach(c -> exploration.addChunk(mapState.dimension, c, true));
		}
		Set<RegistryEntry<MapDecorationType>> recordIcons = Surveyor.CONFIG.builtins.recordIcons();
		WorldLandmarks landmarks = ServerSummary.of(((AccessServerPlayerEntity) player).getServer()).getWorld(mapState.dimension).landmarks();
		UUID owner = Surveyor.getUuid(player);
		if (landmarks != null) {
			for (MapDecoration icon : mapState.getDecorations()) {
				if (recordIcons.contains(icon.type())) {
					int x = MathHelper.floor(((((double) icon.x()) - 0.5) / 2.0) * blocksPerPixel + mapState.centerX);
					int z = MathHelper.floor(((((double) icon.z()) - 0.5) / 2.0) * blocksPerPixel + mapState.centerZ);
					Identifier id = Identifier.of("minecraft", "map/%s/%d/%d".formatted(icon.getAssetId().getPath().toLowerCase(), x, z));
					if (!landmarks.contains(owner, id)) {
						Item item = VANILLA_ICON_STACKS.getOrDefault(icon.type(), Items.STICK);
						exploredWaypoints.add(Landmark.create(owner, id, b -> b
							.add(LandmarkComponentTypes.POS, new BlockPos(x, 0, z))
							.add(LandmarkComponentTypes.NAME, icon.name().orElse(item instanceof BannerItem ? item.getName(item.getDefaultStack()) : Text.literal(WordUtils.capitalizeFully(icon.type().getIdAsString().replace("minecraft:", "").replace(":", " ").replace('_', ' ')))))
							.add(LandmarkComponentTypes.STACK, item.getDefaultStack().copy())
						));
					}
				}
			}
			exploredWaypoints.forEach(landmarks::put);
		}
		if (!exploredChunks.isEmpty() || !exploredWaypoints.isEmpty()) {
			player.sendMessage(Text.empty()
					.append(Text.literal("Recorded ").formatted(Formatting.GREEN))
					.append(exploredChunks.isEmpty() ? Text.empty() : Text.literal("%d".formatted(exploredChunks.size())))
					.append(exploredChunks.isEmpty() ? Text.empty() : Text.literal(" new chunks").formatted(Formatting.GREEN))
					.append(exploredChunks.isEmpty() || exploredWaypoints.isEmpty() ? Text.empty() : Text.literal(" and ").formatted(Formatting.GREEN))
					.append(exploredWaypoints.isEmpty() ? Text.empty() : Text.literal("%d".formatted(exploredWaypoints.size())))
					.append(exploredWaypoints.isEmpty() ? Text.empty() : Text.literal(" new waypoints").formatted(Formatting.GREEN))
					.append(Text.literal("!").formatted(Formatting.GREEN)));
		}
	}


	public static void fillExplorationMap(ServerSummary summary, SurveyorExploration exploration, MapState state) {
		WorldTerrain terrain = summary.getWorld(state.dimension).terrain();
		if (terrain == null) return;
		int blocksPerPixel = 1 << state.scale;
		Boolean[] biomeIsAquatic = new Boolean[16384];
		int offsetX = state.centerX / blocksPerPixel - 64;
		int offsetZ = state.centerZ / blocksPerPixel - 64;
		Map<ChunkPos, LayerSummary.Raw> chunks = new HashMap<>();
		Registry<Biome> biomeRegistry = summary.getServer().getRegistryManager().getOrThrow(RegistryKeys.BIOME);

		for (int pixelX = 0; pixelX < 128; pixelX++) {
			for (int pixelZ = 0; pixelZ < 128; pixelZ++) {
				BlockPos blockPos = new BlockPos((offsetX + pixelZ) * blocksPerPixel, 0, (offsetZ + pixelX) * blocksPerPixel);
				ChunkPos chunkPos = ChunkPos.fromBlockPos(blockPos);
				if (!terrain.contains(chunkPos) || (exploration != null && !exploration.exploredChunk(state.dimension, chunkPos))) continue;
				LayerSummary.Raw chunk = chunks.computeIfAbsent(chunkPos, k -> terrain.get(k).toSingleLayer(null, null, 999));
				if (chunk == null) continue;
				RegistryEntry<Biome> biome = biomeRegistry.getEntry(terrain.getBiomePalette(chunkPos).get(chunk.biomes()[16 * (blockPos.getX() - chunkPos.getStartX()) + blockPos.getZ() - chunkPos.getStartZ()]));
				biomeIsAquatic[pixelX * 128 + pixelZ] = biome.isIn(BiomeTags.WATER_ON_MAP_OUTLINES);
			}
		}

		for (int pixelX = 1; pixelX < 127; pixelX++) {
			for (int pixelZ = 1; pixelZ < 127; pixelZ++) {
				if (state.colors[pixelX + pixelZ * 128] != MapColor.CLEAR.id) continue; // avoid overwriting real maps with explorer ones
				if (biomeIsAquatic[(pixelZ - 1) * 128 + pixelX - 1] == null
					|| biomeIsAquatic[(pixelZ - 1) * 128 + pixelX] == null
					|| biomeIsAquatic[(pixelZ - 1) * 128 + pixelX + 1] == null
					|| biomeIsAquatic[(pixelZ) * 128 + pixelX - 1] == null
					|| biomeIsAquatic[(pixelZ) * 128 + pixelX] == null
					|| biomeIsAquatic[(pixelZ) * 128 + pixelX + 1] == null
					|| biomeIsAquatic[(pixelZ + 1) * 128 + pixelX - 1] == null
					|| biomeIsAquatic[(pixelZ + 1) * 128 + pixelX] == null
					|| biomeIsAquatic[(pixelZ + 1) * 128 + pixelX + 1] == null
				) continue;
				int noise = 0;

				for (int xNoise = -1; xNoise < 2; xNoise++) {
					for (int zNoise = -1; zNoise < 2; zNoise++) {
						if ((xNoise != 0 || zNoise != 0) && biomeIsAquatic[(pixelZ + zNoise) * 128 + pixelX + xNoise]) {
							noise++;
						}
					}
				}

				MapColor.Brightness brightness = MapColor.Brightness.LOWEST;
				MapColor mapColor = MapColor.CLEAR;
				if (biomeIsAquatic[pixelZ * 128 + pixelX]) {
					mapColor = MapColor.ORANGE;
					if (noise > 7 && pixelZ % 2 == 0) {
						brightness = switch ((pixelX + (int) (MathHelper.sin(pixelZ + 0.0F) * 7.0F)) / 8 % 5) {
							case 0, 4 -> MapColor.Brightness.LOW;
							case 1, 3 -> MapColor.Brightness.NORMAL;
							case 2 -> MapColor.Brightness.HIGH;
							default -> brightness;
						};
					} else if (noise > 7) {
						mapColor = MapColor.CLEAR;
					} else if (noise > 5) {
						brightness = MapColor.Brightness.NORMAL;
					} else if (noise > 3) {
						brightness = MapColor.Brightness.LOW;
					} else if (noise > 1) {
						brightness = MapColor.Brightness.LOW;
					}
				} else if (noise > 0) {
					mapColor = MapColor.BROWN;
					if (noise > 3) {
						brightness = MapColor.Brightness.NORMAL;
					}
				}

				if (mapColor != MapColor.CLEAR) {
					state.setColor(pixelX, pixelZ, mapColor.getRenderColorByte(brightness));
				}
			}
		}
	}

	public static void fillMap(ServerSummary summary, SurveyorExploration exploration, MapState state) {
		WorldSummary worldSummary = summary.getWorld(state.dimension);
		WorldTerrain terrain = worldSummary.terrain();
		DimensionType dim = worldSummary.world().getDimension();
		if (terrain == null) return;
		int blocksPerPixel = 1 << state.scale;
		Map<ChunkPos, LayerSummary.Raw> chunks = new HashMap<>();

		BlockPos.Mutable mutable = new BlockPos.Mutable();

		for (int pixelX = 0; pixelX < 128; pixelX++) {
			double prevHeight = 0.0;
			for (int pixelZ = 0; pixelZ < 128; pixelZ++) {
				int blockX = (state.centerX / blocksPerPixel + pixelX - 64) * blocksPerPixel;
				int blockZ = (state.centerZ / blocksPerPixel + pixelZ - 64) * blocksPerPixel;
				Multiset<MapColor> multiset = LinkedHashMultiset.create();
				ChunkPos chunkPos = new ChunkPos(ChunkSectionPos.getSectionCoord(blockX), ChunkSectionPos.getSectionCoord(blockZ));
				if (!terrain.contains(chunkPos) || (exploration != null && !exploration.exploredChunk(state.dimension, chunkPos))) continue;
				LayerSummary.Raw chunk = chunks.computeIfAbsent(chunkPos, k -> terrain.get(k).toSingleLayer(null, null, 999));
				if (chunk != null) {
					int t = 0;
					double height = 0.0;
					if (dim.hasCeiling()) {
						int u = blockX + blockZ * 231871;
						u = u * u * 31287121 + u * 11;
						if ((u >> 20 & 1) == 0) {
							multiset.add(Blocks.DIRT.getDefaultMapColor(), 10);
						} else {
							multiset.add(Blocks.STONE.getDefaultMapColor(), 100);
						}

						height = 100.0;
					} else {
						for (int u = 0; u < blocksPerPixel; u++) {
							for (int v = 0; v < blocksPerPixel; v++) {
								mutable.set(blockX + u, 0, blockZ + v);
								int index = 16 * (mutable.getX() - chunkPos.getStartX()) + mutable.getZ() - chunkPos.getStartZ();
								if (!chunk.exists().get(index)) continue;
								int y = 999 - chunk.depths()[index] + 1;
								height += (double) y / (blocksPerPixel * blocksPerPixel);
								t = chunk.waterDepths()[index];
								MapColor block = chunk.waterDepths()[index] > 0 ? MapColor.WATER_BLUE : terrain.getBlockPalette(chunkPos).get(chunk.blocks()[index]).getDefaultMapColor();
								multiset.add(block);
							}
						}
					}

					MapColor mapColor = Iterables.getFirst(Multisets.copyHighestCountFirst(multiset), MapColor.CLEAR);
					MapColor.Brightness brightness;
					if (mapColor == MapColor.WATER_BLUE) {
						double heightShading = t * 0.1 + (pixelX + pixelZ & 1) * 0.2;
						if (heightShading < 0.5) {
							brightness = MapColor.Brightness.HIGH;
						} else if (heightShading > 0.9) {
							brightness = MapColor.Brightness.LOW;
						} else {
							brightness = MapColor.Brightness.NORMAL;
						}
					} else {
						double heightShading = (height - prevHeight) * 4.0 / (blocksPerPixel + 4) + ((pixelX + pixelZ & 1) - 0.5) * 0.4;
						if (heightShading > 0.6) {
							brightness = MapColor.Brightness.HIGH;
						} else if (heightShading < -0.6) {
							brightness = MapColor.Brightness.LOW;
						} else {
							brightness = MapColor.Brightness.NORMAL;
						}
					}

					prevHeight = height;
					state.putColor(pixelX, pixelZ, mapColor.getRenderColorByte(brightness));
				}
			}
		}
	}
}
