package folk.sisby.surveyor.client;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import folk.sisby.surveyor.PlayerSummary;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.SurveyorExploration;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.config.SystemMode;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.LandmarkType;
import folk.sisby.surveyor.landmark.Landmarks;
import folk.sisby.surveyor.landmark.SimplePointLandmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.util.TextUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.DefaultPosArgument;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class SurveyorClientCommands {
	private static void informGroup(Map<UUID, PlayerSummary> group, Consumer<Text> feedback) {
		feedback.accept(
			Text.literal("You're sharing your map with ").formatted(Formatting.GOLD)
				.append(Text.literal("%d".formatted(group.size() - 1)).formatted(Formatting.WHITE))
				.append(Text.literal(" other" + (group.size() - 1 > 1 ? " players:" : " player:")).formatted(Formatting.GOLD))
		);
		feedback.accept(
			TextUtil.highlightStrings(group.entrySet().stream().filter(e -> !e.getKey().equals(SurveyorClient.getClientUuid())).map(e -> e.getValue().username()).toList(), s -> Formatting.WHITE).formatted(Formatting.GOLD)
		);
	}

	private static int info(WorldSummary summary, SurveyorExploration exploration, SurveyorExploration groupExploration, Consumer<Text> feedback) {
		Set<Landmark<?>> landmarks = new HashSet<>();
		Set<Landmark<?>> waypoints = new HashSet<>();
		Set<Landmark<?>> groupLandmarks = new HashSet<>();
		Set<Landmark<?>> groupWaypoints = new HashSet<>();
		Map<UUID, PlayerSummary> group = SurveyorClient.getFriends();
		WorldLandmarks worldLandmarks = summary.landmarks();
		if (worldLandmarks != null) {
			worldLandmarks.asMap(exploration).forEach((type, inner) -> inner.forEach((pos, landmark) -> (landmark.owner() == null ? landmarks : waypoints).add(landmark)));
			worldLandmarks.asMap(groupExploration).forEach((type, inner) -> inner.forEach((pos, landmark) -> (landmark.owner() == null ? groupLandmarks : groupWaypoints).add(landmark)));
		}
		feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("---Map Exploration Summary---").formatted(Formatting.GRAY)));
		feedback.accept(
			Text.literal("You've explored ").formatted(Formatting.AQUA)
				.append(Text.literal("%d".formatted(exploration.chunkCount())).formatted(Formatting.WHITE))
				.append(Text.literal(" total chunks!").formatted(Formatting.AQUA))
				.append(
					group.size() <= 1 ? Text.empty() :
						Text.literal(" (").formatted(Formatting.AQUA)
							.append(Text.literal("%d".formatted(groupExploration.chunkCount())).formatted(Formatting.WHITE))
							.append(Text.literal(" with friends)").formatted(Formatting.AQUA))
				)
		);
		feedback.accept(
			Text.literal("You've explored ").formatted(Formatting.LIGHT_PURPLE)
				.append(Text.literal("%d".formatted(exploration.structureCount())).formatted(Formatting.WHITE))
				.append(Text.literal(" structures!").formatted(Formatting.LIGHT_PURPLE))
				.append(
					group.size() <= 1 ? Text.empty() :
						Text.literal(" (").formatted(Formatting.LIGHT_PURPLE)
							.append(Text.literal("%d".formatted(groupExploration.structureCount())).formatted(Formatting.WHITE))
							.append(Text.literal(" with friends)").formatted(Formatting.LIGHT_PURPLE))
				)
		);
		feedback.accept(
			Text.literal("You've explored ").formatted(Formatting.GREEN)
				.append(Text.literal("%d".formatted(landmarks.size())).formatted(Formatting.WHITE))
				.append(Text.literal(" landmarks here!").formatted(Formatting.GREEN))
				.append(
					group.size() <= 1 ? Text.empty() :
						Text.literal(" (").formatted(Formatting.GREEN)
							.append(Text.literal("%d".formatted(groupLandmarks.size())).formatted(Formatting.WHITE))
							.append(Text.literal(" with friends)").formatted(Formatting.GREEN))
				)
		);
		feedback.accept(
			Text.literal("...and created ").formatted(Formatting.GREEN)
				.append(Text.literal("%d".formatted(waypoints.size())).formatted(Formatting.WHITE))
				.append(Text.literal(" waypoints!").formatted(Formatting.GREEN))
				.append(
					group.size() <= 1 ? Text.empty() :
						Text.literal(" (").formatted(Formatting.GREEN)
							.append(Text.literal("%d".formatted(groupWaypoints.size())).formatted(Formatting.WHITE))
							.append(Text.literal(" with friends)").formatted(Formatting.GREEN))
				)
		);
		if (group.size() > 1) {
			informGroup(SurveyorClient.getFriends(), feedback);
		}
		feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("-------End Summary-------").formatted(Formatting.GRAY)));
		return 1;
	}

	private static int landmarkInfo(WorldSummary summary, SurveyorExploration exploration, SurveyorExploration groupExploration, Consumer<Text> feedback) {
		Collection<PlayerSummary> group = SurveyorClient.getFriends().values();
		feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("---World Landmark Types---").formatted(Formatting.GRAY)));
		Set<LandmarkType<?>> waypoints = new HashSet<>();
		Multimap<LandmarkType<?>, BlockPos> keys = HashMultimap.create();
		Multimap<LandmarkType<?>, BlockPos> groupKeys = HashMultimap.create();
		Multimap<LandmarkType<?>, BlockPos> personalKeys = HashMultimap.create();
		WorldLandmarks worldLandmarks = summary.landmarks();
		if (worldLandmarks != null) {
			waypoints.addAll(worldLandmarks.asMap(null).values().stream().flatMap(e -> e.values().stream().filter(l -> l.owner() != null)).map(Landmark::type).toList());
			keys.putAll(worldLandmarks.keySet(null));
			groupKeys.putAll(worldLandmarks.keySet(groupExploration));
			personalKeys.putAll(worldLandmarks.keySet(exploration));
		}
		keys.asMap().forEach((type, list) -> feedback.accept(
			Text.literal("%s".formatted(type.id())).formatted(Formatting.WHITE)
				.append(Text.literal(waypoints.contains(type) ? ": created " : ": explored ").formatted(Formatting.AQUA))
				.append(Text.literal("%d".formatted(personalKeys.get(type).size())).formatted(Formatting.WHITE))
				.append(
					group.size() <= 1 ? Text.empty() :
						Text.literal(" (").formatted(Formatting.LIGHT_PURPLE)
							.append(Text.literal("%d".formatted(groupKeys.get(type).size())).formatted(Formatting.WHITE)) // TODO: counting on this is wrong
							.append(Text.literal(" shared)").formatted(Formatting.LIGHT_PURPLE))
				)
				.append(
					Text.literal(" {of ").formatted(Formatting.GOLD)
						.append(Text.literal("%d".formatted(keys.get(type).size())).formatted(Formatting.WHITE))
						.append(Text.literal("}").formatted(Formatting.GOLD))
				)
		));
		feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("-------End Landmarks-------").formatted(Formatting.GRAY)));
		return 1;
	}

	private static int getLandmarks(WorldSummary summary, Consumer<Text> feedback, Identifier type) {
		if (summary.landmarks() == null) {
			feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("The landmark system is dynamically disabled!").formatted(Formatting.YELLOW)));
			return 0;
		}
		Map<BlockPos, ? extends Landmark<?>> landmarks = summary.landmarks().asMap(Landmarks.getType(type), null);
		if (landmarks.isEmpty()) {
			feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("There are no landmarks of that type in this world!").formatted(Formatting.YELLOW)));
			return 0;
		}
		feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("---World %s Landmarks---".formatted(type)).formatted(Formatting.GRAY)));
		for (Landmark<?> landmark : landmarks.values()) {
			feedback.accept(
				Text.literal("[").formatted(Formatting.AQUA)
					.append(Text.literal(landmark.pos().toShortString()).formatted(Formatting.WHITE))
					.append(Text.literal("]").formatted(Formatting.AQUA))
					.append(Text.literal(" - ").formatted(landmark.owner() != null ? Formatting.GREEN : Formatting.RED))
					.append(Text.literal("\"").formatted(Formatting.GOLD))
					.append(landmark.name() == null ? Text.of("") : landmark.name().copy().styled(s -> s.withColor(landmark.color() != null ? landmark.color().getFireworkColor() : Formatting.WHITE.getColorValue())))
					.append(Text.literal("\"").formatted(Formatting.GOLD))
			);
		}
		feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("---End %s Landmarks---".formatted(type)).formatted(Formatting.GRAY)));
		return landmarks.size();
	}

	private static int removeLandmark(WorldSummary summary, ClientWorld world, Consumer<Text> feedback, Identifier type, BlockPos pos) {
		if (summary.landmarks() == null) {
			feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("The landmark system is dynamically disabled!").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (!summary.landmarks().contains(Landmarks.getType(type), pos)) {
			feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("No landmark exists of that type and position!").formatted(Formatting.YELLOW)));
			return 0;
		}
		Landmark<?> landmark = summary.landmarks().get(Landmarks.getType(type), pos);
		summary.landmarks().remove(world, Landmarks.getType(type), pos);
		feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("%s removed successfully!".formatted(landmark.owner() == null ? "Landmark" : "Waypoint")).formatted(Formatting.GREEN)));
		return 1;
	}

	private static int addLandmark(WorldSummary summary, ClientWorld world, Consumer<Text> feedback, Identifier type, BlockPos pos, DyeColor color, String name, boolean global) {
		if (summary.landmarks() == null) {
			feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("The landmark system is dynamically disabled!").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (!SimplePointLandmark.TYPE.id().equals(type)) {
			feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("You can't create a landmark of that type!").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (summary.landmarks().contains(Landmarks.getType(type), pos)) {
			feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("A landmark exists of that type and position!").formatted(Formatting.YELLOW)));
			return 0;
		}
		summary.landmarks().put(world, new SimplePointLandmark(pos, global ? null : SurveyorClient.getClientUuid(), color, Text.of(name), null));
		feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("%s added successfully!".formatted(global ? "Landmark" : "Waypoint")).formatted(Formatting.GREEN)));
		return 1;
	}

	public static int execute(CommandContext<FabricClientCommandSource> context, SurveyorCommandExecutor<Integer> executor) {
		ClientPlayerEntity player = context.getSource().getPlayer();
		SurveyorExploration exploration = SurveyorClient.getPersonalExploration();
		SurveyorExploration groupExploration = SurveyorClient.getSharedExploration();
		try {
			return executor.execute(WorldSummary.of(context.getSource().getWorld()), player, context.getSource().getWorld(), exploration, groupExploration, t -> context.getSource().sendFeedback(t));
		} catch (Exception e) {
			context.getSource().sendFeedback(Text.literal("Command failed! Check log for details.").formatted(Formatting.RED));
			Surveyor.LOGGER.error("[Surveyor] Error while executing command: {}", context.getInput(), e);
			return 0;
		}
	}

	private static ServerCommandSource sourceForPos(FabricClientCommandSource source) {
		return new ServerCommandSource(null, source.getPosition(), source.getRotation(), null, 0, null, null, null, source.getEntity());
	}

	public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
		dispatcher.register(
			ClientCommandManager.literal("surveyorc")
				.executes(c -> execute(c, (s, p, w, e, g, f) -> SurveyorClientCommands.info(s, e, g, f)))
				.then(ClientCommandManager.literal("landmarks")
					.requires(c -> Surveyor.CONFIG.landmarks != SystemMode.DISABLED)
					.executes(c -> execute(c, (s, p, w, e, g, f) -> SurveyorClientCommands.landmarkInfo(s, e, g, f)))
					.then(ClientCommandManager.literal("get")
						.then(ClientCommandManager.argument("type", IdentifierArgumentType.identifier())
							.suggests((c, b) -> CommandSource.suggestIdentifiers(Landmarks.keySet(), b))
							.executes(c -> execute(c, (s, p, w, e, g, f) -> SurveyorClientCommands.getLandmarks(s, f, c.getArgument("type", Identifier.class))))
						)
					)
					.then(ClientCommandManager.literal("remove")
						.requires(c -> Surveyor.CONFIG.landmarks != SystemMode.FROZEN)
						.then(ClientCommandManager.argument("type", IdentifierArgumentType.identifier())
							.suggests((c, b) -> CommandSource.suggestIdentifiers(Landmarks.keySet(), b))
							.then(ClientCommandManager.argument("pos", BlockPosArgumentType.blockPos())
								.executes(c -> execute(c, (s, p, w, e, g, f) -> SurveyorClientCommands.removeLandmark(s, w, f, c.getArgument("type", Identifier.class), c.getArgument("pos", DefaultPosArgument.class).toAbsoluteBlockPos(sourceForPos(c.getSource())))))
							)
						)
					).then(ClientCommandManager.literal("add")
						.requires(c -> Surveyor.CONFIG.landmarks != SystemMode.FROZEN)
						.then(ClientCommandManager.argument("type", IdentifierArgumentType.identifier())
							.suggests((c, b) -> CommandSource.suggestIdentifiers(List.of(SimplePointLandmark.TYPE.id()), b))
							.then(ClientCommandManager.argument("pos", BlockPosArgumentType.blockPos())
								.then(ClientCommandManager.argument("color", StringArgumentType.word())
									.suggests((c, b) -> CommandSource.suggestMatching(Arrays.stream(DyeColor.values()).map(DyeColor::getId), b))
									.then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
										.executes(c -> execute(c, (s, p, w, e, g, f) -> SurveyorClientCommands.addLandmark(s, w, f, c.getArgument("type", Identifier.class), c.getArgument("pos", DefaultPosArgument.class).toAbsoluteBlockPos(sourceForPos(c.getSource())), DyeColor.byId(c.getArgument("color", String.class), DyeColor.WHITE), c.getArgument("name", String.class), false)))
									)
								)
							)
						)
					).then(ClientCommandManager.literal("global")
						.requires(c -> Surveyor.CONFIG.landmarks != SystemMode.FROZEN)
						.requires(c -> c.getPlayer() == null || c.getPlayer().hasPermissionLevel(2))
						.then(ClientCommandManager.argument("type", IdentifierArgumentType.identifier())
							.suggests((c, b) -> CommandSource.suggestIdentifiers(List.of(SimplePointLandmark.TYPE.id()), b))
							.then(ClientCommandManager.argument("pos", BlockPosArgumentType.blockPos())
								.then(ClientCommandManager.argument("color", StringArgumentType.word())
									.suggests((c, b) -> CommandSource.suggestMatching(Arrays.stream(DyeColor.values()).map(DyeColor::getId), b))
									.then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
										.executes(c -> execute(c, (s, p, w, e, g, f) -> SurveyorClientCommands.addLandmark(s, w, f, c.getArgument("type", Identifier.class), c.getArgument("pos", DefaultPosArgument.class).toAbsoluteBlockPos(sourceForPos(c.getSource())), DyeColor.byId(c.getArgument("color", String.class), DyeColor.WHITE), c.getArgument("name", String.class), true)))
									)
								)
							)
						)
					)
				)
		);
	}

	public interface SurveyorCommandExecutor<T> {
		T execute(WorldSummary currentWorldSummary, ClientPlayerEntity player, ClientWorld world, SurveyorExploration exploration, SurveyorExploration groupExploration, Consumer<Text> feedback);
	}
}
