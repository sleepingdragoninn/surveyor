package folk.sisby.surveyor;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import folk.sisby.surveyor.config.SystemMode;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentMap;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.util.TextUtil;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.DefaultPosArgument;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class SurveyorCommands {
	private static final Multimap<UUID, UUID> requests = HashMultimap.create();

	private static void informGroup(ServerPlayerEntity player, Set<PlayerSummary> group, Consumer<Text> feedback) {
		feedback.accept(
			Text.literal("You're sharing your map with ").formatted(Formatting.GOLD)
				.append(Text.literal("%d".formatted(group.size() - 1)).formatted(Formatting.WHITE))
				.append(Text.literal(" other" + (group.size() - 1 > 1 ? " players:" : " player:")).formatted(Formatting.GOLD))
		);
		feedback.accept(
			TextUtil.highlightStrings(group.stream().map(PlayerSummary::username).filter(u -> !u.equals(player.getGameProfile().getName())).toList(), s -> Formatting.WHITE).formatted(Formatting.GOLD)
		);
	}

	private static int informGlobal(ServerSummary serverSummary, ServerPlayerEntity player, Consumer<Text> feedback) {
		feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("The server has global sharing enabled!").formatted(Formatting.YELLOW)));
		feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("You can't leave or modify the global sharing group!").formatted(Formatting.YELLOW)));
		informGroup(player, serverSummary.groupPlayers(Surveyor.getUuid(player), player.getServer()), feedback);
		return 0;
	}

	private static int info(ServerSummary serverSummary, ServerPlayerEntity player, SurveyorExploration exploration, Consumer<Text> feedback) {
		Set<PlayerSummary> group = serverSummary.groupPlayers(Surveyor.getUuid(player), player.getServer());
		SurveyorExploration groupExploration = SurveyorExploration.ofShared(player);
		Set<Landmark> landmarks = new HashSet<>();
		Set<Landmark> waypoints = new HashSet<>();
		Set<Landmark> groupLandmarks = new HashSet<>();
		Set<Landmark> groupWaypoints = new HashSet<>();
		for (ServerWorld world : player.getServer().getWorlds()) {
			WorldLandmarks summary = WorldSummary.of(world).landmarks();
			if (summary != null) {
				summary.asMap(exploration).forEach((type, inner) -> inner.forEach((id, landmark) -> (landmark.owner() == null ? landmarks : waypoints).add(landmark)));
				summary.asMap(groupExploration).forEach((type, inner) -> inner.forEach((id, landmark) -> (landmark.owner() == null ? groupLandmarks : groupWaypoints).add(landmark)));
			}
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
				.append(Text.literal(" landmarks!").formatted(Formatting.GREEN))
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
			informGroup(player, group, feedback);
		}
		feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("-------End Summary-------").formatted(Formatting.GRAY)));
		return 1;
	}

	private static int landmarkInfo(ServerSummary serverSummary, ServerPlayerEntity player, SurveyorExploration exploration, Consumer<Text> feedback) {
		Set<PlayerSummary> group = serverSummary.groupPlayers(Surveyor.getUuid(player), player.getServer());
		SurveyorExploration groupExploration = SurveyorExploration.ofShared(player);
		feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("---Landmark Types---").formatted(Formatting.GRAY)));
		Set<UUID> waypoints = new HashSet<>();
		Multimap<UUID, Identifier> keys = HashMultimap.create();
		Multimap<UUID, Identifier> groupKeys = HashMultimap.create();
		Multimap<UUID, Identifier> personalKeys = HashMultimap.create();
		for (ServerWorld world : player.getServer().getWorlds()) {
			WorldLandmarks summary = WorldSummary.of(world).landmarks();
			if (summary != null) {
				waypoints.addAll(summary.asMap(null).values().stream().flatMap(e -> e.values().stream().filter(l -> l.owner() != null)).map(Landmark::owner).toList());
				keys.putAll(summary.keySet(null));
				groupKeys.putAll(summary.keySet(groupExploration));
				personalKeys.putAll(summary.keySet(exploration));
			}
		}
		keys.asMap().forEach((type, list) -> feedback.accept(
			Text.literal("%s".formatted(type)).formatted(Formatting.WHITE)
				.append(Text.literal(waypoints.contains(type) ? ": created " : ": explored ").formatted(Formatting.AQUA))
				.append(Text.literal("%d".formatted(personalKeys.get(type).size())).formatted(Formatting.WHITE))
				.append(
					group.size() <= 1 ? Text.empty() :
						Text.literal(" (").formatted(Formatting.LIGHT_PURPLE)
							.append(Text.literal("%d".formatted(groupKeys.get(type).size())).formatted(Formatting.WHITE))
							.append(Text.literal(" shared)").formatted(Formatting.LIGHT_PURPLE))
				)
				.append(
					!player.hasPermissionLevel(2) ? Text.empty() :
						Text.literal(" {of ").formatted(Formatting.GOLD)
							.append(Text.literal("%d".formatted(keys.get(type).size())).formatted(Formatting.WHITE))
							.append(Text.literal("}").formatted(Formatting.GOLD))
				)
		));
		feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("-------End Landmarks-------").formatted(Formatting.GRAY)));
		return 1;
	}

	private static int share(ServerSummary serverSummary, ServerPlayerEntity player, Consumer<Text> feedback, String username) {
		ServerPlayerEntity sharePlayer = player.getServer().getPlayerManager().getPlayer(username);
		if (sharePlayer == null) {
			feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("Can't find an online player named ").formatted(Formatting.YELLOW)).append(Text.literal(username).formatted(Formatting.WHITE)).append(Text.literal(".").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (sharePlayer == player) {
			feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("You can't share map exploration with yourself!").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (requests.containsEntry(Surveyor.getUuid(player), Surveyor.getUuid(sharePlayer))) { // Accept Request
			if (serverSummary.groupSize(Surveyor.getUuid(player)) > 1 && serverSummary.groupSize(Surveyor.getUuid(sharePlayer)) > 1) {
				feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("You're in a group! leave your group first with:").formatted(Formatting.YELLOW)));
				feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("/surveyor unshare").formatted(Formatting.GOLD)));
				return 0;
			}
			requests.removeAll(Surveyor.getUuid(player)); // clear all other requests
			ServerSummary.of(player.getServer()).joinGroup(Surveyor.getUuid(player), Surveyor.getUuid(sharePlayer), player.getServer());
			feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("You're now sharing map exploration with ").formatted(Formatting.GREEN)).append(Text.literal("%d".formatted(serverSummary.groupSize(Surveyor.getUuid(player)) - 1)).formatted(Formatting.WHITE)).append(Text.literal((serverSummary.groupSize(Surveyor.getUuid(player)) - 1) > 1 ? " players:" : " player:").formatted(Formatting.GREEN)));
			feedback.accept(TextUtil.highlightStrings(serverSummary.groupPlayers(Surveyor.getUuid(player), player.getServer()).stream().map(PlayerSummary::username).filter(u -> !u.equals(player.getGameProfile().getName())).toList(), s -> Formatting.WHITE).formatted(Formatting.GREEN));
			for (ServerPlayerEntity friend : serverSummary.groupOtherServerPlayers(Surveyor.getUuid(player), player.getServer())) {
				friend.sendMessage(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(player.getDisplayName().copy().formatted(Formatting.WHITE)).append(Text.literal(" is now sharing their map with you.").formatted(Formatting.AQUA)));
				friend.sendMessage(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("You're now sharing map exploration with ").formatted(Formatting.AQUA)).append(Text.literal("%d".formatted(serverSummary.groupSize(Surveyor.getUuid(player)) - 1)).formatted(Formatting.WHITE)).append(Text.literal((serverSummary.groupSize(Surveyor.getUuid(player)) - 1) > 1 ? " players:" : " player:").formatted(Formatting.AQUA)));
				friend.sendMessage(TextUtil.highlightStrings(serverSummary.groupPlayers(Surveyor.getUuid(player), player.getServer()).stream().map(PlayerSummary::username).filter(u -> !u.equals(friend.getGameProfile().getName())).toList(), s -> Formatting.WHITE).formatted(Formatting.AQUA));
			}
			return 1;
		} else if (!requests.containsEntry(Surveyor.getUuid(sharePlayer), Surveyor.getUuid(player))) { // Make Request
			requests.put(Surveyor.getUuid(sharePlayer), Surveyor.getUuid(player));
			feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("Share request sent to ").formatted(Formatting.GREEN)).append(sharePlayer.getDisplayName().copy().formatted(Formatting.WHITE)).append(Text.literal(".").formatted(Formatting.GREEN)));
			sharePlayer.sendMessage(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(player.getDisplayName().copy().formatted(Formatting.WHITE)).append(Text.literal(" wants to share map exploration!").formatted(Formatting.AQUA)));
			if (serverSummary.groupSize(Surveyor.getUuid(player)) <= 1 && serverSummary.groupSize(Surveyor.getUuid(sharePlayer)) <= 1) { // Creating a group
				feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("If accepted, you'll share your map exploration.").formatted(Formatting.GREEN)));
				sharePlayer.sendMessage(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("To share your explored map area, enter:").formatted(Formatting.AQUA)));
			} else if (serverSummary.groupSize(Surveyor.getUuid(player)) <= 1) { // Joining their group
				feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("If accepted, you'll share with their group of ").formatted(Formatting.GREEN)).append(Text.literal("%d".formatted(serverSummary.groupSize(Surveyor.getUuid(sharePlayer)))).formatted(Formatting.WHITE)).append(Text.literal(".").formatted(Formatting.GREEN)));
				sharePlayer.sendMessage(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("To share your group of ").append(Text.literal("%d".formatted(serverSummary.groupSize(Surveyor.getUuid(sharePlayer)))).formatted(Formatting.WHITE)).formatted(Formatting.AQUA)).append(Text.literal(", enter:").formatted(Formatting.AQUA)));
			} else { // Sharing your group
				feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("If accepted, they'll share with your group of ").formatted(Formatting.GREEN)).append(Text.literal("%d".formatted(serverSummary.groupSize(Surveyor.getUuid(player)))).formatted(Formatting.WHITE)).append(Text.literal(".").formatted(Formatting.GREEN)));
				sharePlayer.sendMessage(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("To share with their group of ").append(Text.literal("%d".formatted(serverSummary.groupSize(Surveyor.getUuid(player)))).formatted(Formatting.WHITE)).formatted(Formatting.AQUA)).append(Text.literal(", enter:").formatted(Formatting.AQUA)));
			}
			sharePlayer.sendMessage(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("/surveyor share %s".formatted(player.getGameProfile().getName())).formatted(Formatting.GOLD)));
			return 1;
		} else {
			feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("You've already sent this player a share request!").formatted(Formatting.YELLOW)));
			return 0;
		}
	}

	private static int unshare(ServerSummary serverSummary, ServerPlayerEntity player, Consumer<Text> feedback) {
		int shareNumber = serverSummary.groupSize(Surveyor.getUuid(player)) - 1;
		if (shareNumber == 0) {
			feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("You're not sharing map exploration with anyone!").formatted(Formatting.YELLOW)));
			return 0;
		} else {
			Set<ServerPlayerEntity> friends = serverSummary.groupOtherServerPlayers(Surveyor.getUuid(player), player.getServer());
			ServerSummary.of(player.getServer()).leaveGroup(Surveyor.getUuid(player), player.getServer());
			feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("Stopped sharing map exploration with ").formatted(Formatting.GREEN)).append(Text.literal("%d".formatted(shareNumber)).formatted(Formatting.WHITE)).append(Text.literal(shareNumber > 1 ? " players." : " player.").formatted(Formatting.GREEN)));
			for (ServerPlayerEntity friend : friends) {
				int groupSize = serverSummary.groupSize(Surveyor.getUuid(friend)) - 1;
				friend.sendMessage(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(player.getDisplayName().copy().formatted(Formatting.WHITE)).append(Text.literal(" is no longer sharing with you.").formatted(Formatting.AQUA)));
				friend.sendMessage(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("You're now sharing map exploration with ").formatted(Formatting.AQUA)).append(Text.literal("%d".formatted(groupSize)).formatted(Formatting.WHITE)).append(Text.literal(groupSize == 0 ? " players." : groupSize > 1 ? " players:" : " player:").formatted(Formatting.AQUA)));
				if (groupSize > 0) friend.sendMessage(TextUtil.highlightStrings(serverSummary.groupPlayers(Surveyor.getUuid(friend), friend.getServer()).stream().map(PlayerSummary::username).filter(u -> !u.equals(friend.getGameProfile().getName())).toList(), s -> Formatting.WHITE).formatted(Formatting.AQUA));
			}
			return 1;
		}
	}

	private static int getLandmarks(WorldSummary summary, ServerPlayerEntity player, SurveyorExploration exploration, Consumer<Text> feedback, boolean global) {
		if (summary.landmarks() == null) {
			feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("The landmark system is dynamically disabled!").formatted(Formatting.YELLOW)));
			return 0;
		}
		Map<Identifier, Landmark> landmarks = summary.landmarks().asMap(global ? WorldLandmarks.GLOBAL : player.getUuid(), player.hasPermissionLevel(2) ? null : exploration);
		if (landmarks.isEmpty()) {
			feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("There are no landmarks in this world!").formatted(Formatting.YELLOW)));
			return 0;
		}
		feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("---World %s---".formatted(global ? "Landmarks" : "Waypoints")).formatted(Formatting.GRAY)));
		for (Landmark landmark : landmarks.values()) {
			feedback.accept(
				Text.literal(landmark.id().toString()).append(!landmark.components().contains(LandmarkComponentTypes.POS) ? Text.literal("") :
						Text.literal(" [").formatted(Formatting.AQUA)
							.append(Text.literal(landmark.components().get(LandmarkComponentTypes.POS).toShortString()).formatted(Formatting.WHITE))
							.append(Text.literal("]").formatted(Formatting.AQUA))
					)
					.append(Text.literal(" - ").formatted(landmark.owner() != null ? Formatting.GREEN : Formatting.RED))
					.append(Text.literal("\"").formatted(Formatting.GOLD))
					.append(!landmark.components().contains(LandmarkComponentTypes.NAME) ? Text.of("") : landmark.components().get(LandmarkComponentTypes.NAME).copy().styled(s -> s.withColor(landmark.components().contains(LandmarkComponentTypes.COLOR) ? landmark.components().get(LandmarkComponentTypes.COLOR) : Formatting.WHITE.getColorValue())))
					.append(Text.literal("\"").formatted(Formatting.GOLD))
			);
		}
		feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("---End %s---".formatted(global ? "Landmarks" : "Waypoints")).formatted(Formatting.GRAY)));
		return landmarks.size();
	}

	private static int removeLandmark(WorldSummary summary, ServerPlayerEntity player, ServerWorld world, Consumer<Text> feedback, Identifier type, boolean global) {
		if (summary.landmarks() == null) {
			feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("The landmark system is dynamically disabled!").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (!summary.landmarks().contains(global ? WorldLandmarks.GLOBAL : player.getUuid(), type)) {
			feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("No landmark exists of that type and position!").formatted(Formatting.YELLOW)));
			return 0;
		}
		Landmark landmark = summary.landmarks().get(global ? WorldLandmarks.GLOBAL : player.getUuid(), type);
		if ((landmark.owner() == null || landmark.owner() != Surveyor.getUuid(player)) && !player.hasPermissionLevel(2)) {
			feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("You don't have permission to delete that landmark!").formatted(Formatting.YELLOW)));
			return 0;
		}
		summary.landmarks().remove(world, global ? WorldLandmarks.GLOBAL : player.getUuid(), type);
		feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("%s removed successfully!".formatted(landmark.owner() == null ? "Landmark" : "Waypoint")).formatted(Formatting.GREEN)));
		return 1;
	}

	private static int addLandmark(WorldSummary summary, ServerPlayerEntity player, ServerWorld world, Consumer<Text> feedback, Identifier id, BlockPos pos, DyeColor color, String name, boolean global) {
		if (summary.landmarks() == null) {
			feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("The landmark system is dynamically disabled!").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (global && !player.hasPermissionLevel(2)) {
			feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("You don't have permission to add that landmark!").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (summary.landmarks().contains(global ? WorldLandmarks.GLOBAL : player.getUuid(), id)) {
			feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("The specified landmark already exists!").formatted(Formatting.YELLOW)));
			return 0;
		}
		summary.landmarks().put(world, new Landmark(global ? WorldLandmarks.GLOBAL : Surveyor.getUuid(player), id, LandmarkComponentMap.builder()
			.add(LandmarkComponentTypes.POS, pos)
			.add(LandmarkComponentTypes.COLOR, color.getFireworkColor())
			.add(LandmarkComponentTypes.NAME, Text.of(name.contains("\\n") ? name.substring(0, name.indexOf("\\n")) : name))
			.add(LandmarkComponentTypes.LORE, name.contains("\\n") ? Arrays.stream(name.substring(name.indexOf("\\n") + 2).split("\\n")).map(Text::of).toList() : null)
			.build()));
		feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("%s added successfully!".formatted(global ? "Landmark" : "Waypoint")).formatted(Formatting.GREEN)));
		return 1;
	}

	public static int execute(CommandContext<ServerCommandSource> context, SurveyorCommandExecutor<Integer> executor) {
		ServerPlayerEntity player;
		try {
			player = context.getSource().getPlayerOrThrow();
		} catch (CommandSyntaxException e) {
			Surveyor.LOGGER.error("[Surveyor] Commands cannot be invoked by a non-player");
			return 0;
		}

		SurveyorExploration exploration = SurveyorExploration.of(player);
		try {
			return executor.execute(ServerSummary.of(player.getServer()), WorldSummary.of(context.getSource().getWorld()), player, context.getSource().getWorld(), exploration, t -> context.getSource().sendFeedback(() -> t, false));
		} catch (Exception e) {
			context.getSource().sendFeedback(() -> Text.literal("Command failed! Check log for details.").formatted(Formatting.RED), false);
			Surveyor.LOGGER.error("[Surveyor] Error while executing command: {}", context.getInput(), e);
			return 0;
		}
	}

	public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
		dispatcher.register(
			CommandManager.literal("surveyor")
				.executes(c -> execute(c, (s, w, p, sw, e, f) -> SurveyorCommands.info(s, p, e, f)))
				.then(Surveyor.CONFIG.networking.globalSharing ?
					CommandManager.literal("share")
						.executes(c -> execute(c, (s, w, p, sw, e, f) -> SurveyorCommands.informGlobal(s, p, f))) :
					CommandManager.literal("share")
						.then(CommandManager.argument("player", StringArgumentType.word())
							.suggests((c, b) -> CommandSource.suggestMatching(c.getSource().getServer().getPlayerManager().getPlayerList().stream().filter(p -> c.getSource().getPlayer() != p).map(p -> p.getGameProfile().getName()), b))
							.executes(c -> execute(c, (s, w, p, sw, e, f) -> SurveyorCommands.share(s, p, f, c.getArgument("player", String.class))))
						)
				).then(Surveyor.CONFIG.networking.globalSharing ?
					CommandManager.literal("unshare")
						.executes(c -> execute(c, (s, w, p, sw, e, f) -> SurveyorCommands.informGlobal(s, p, f))) :
					CommandManager.literal("unshare")
						.executes(c -> execute(c, (s, w, p, sw, e, f) -> SurveyorCommands.unshare(s, p, f)))
				).then(CommandManager.literal("landmarks")
					.requires(c -> Surveyor.CONFIG.landmarks != SystemMode.DISABLED)
					.executes(c -> execute(c, (s, w, p, sw, e, f) -> SurveyorCommands.landmarkInfo(s, p, e, f)))
					.then(CommandManager.literal("get")
						.executes(c -> execute(c, (s, w, p, sw, e, f) -> SurveyorCommands.getLandmarks(w, p, e, f, true))
						)
					)
					.then(CommandManager.literal("remove")
						.requires(c -> Surveyor.CONFIG.landmarks != SystemMode.FROZEN)
						.then(CommandManager.argument("id", IdentifierArgumentType.identifier())
							.executes(c -> execute(c, (s, w, p, sw, e, f) -> SurveyorCommands.removeLandmark(w, p, sw, f, c.getArgument("id", Identifier.class),true))
							)
						)
					).then(CommandManager.literal("add")
						.requires(c -> Surveyor.CONFIG.landmarks != SystemMode.FROZEN)
						.then(CommandManager.argument("id", IdentifierArgumentType.identifier())
							.then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
								.then(CommandManager.argument("color", StringArgumentType.word())
									.suggests((c, b) -> CommandSource.suggestMatching(Arrays.stream(DyeColor.values()).map(DyeColor::getName), b))
									.then(CommandManager.argument("name", StringArgumentType.greedyString())
										.executes(c -> execute(c, (s, w, p, sw, e, f) -> SurveyorCommands.addLandmark(w, p, sw, f, c.getArgument("id", Identifier.class), c.getArgument("pos", DefaultPosArgument.class).toAbsoluteBlockPos(c.getSource()), DyeColor.byName(c.getArgument("color", String.class), DyeColor.WHITE), c.getArgument("name", String.class), false)))
									)
								)
							)
						)
					).then(CommandManager.literal("global")
						.requires(c -> Surveyor.CONFIG.landmarks != SystemMode.FROZEN)
						.requires(c -> c.getPlayer() == null || c.getPlayer().hasPermissionLevel(2))
						.then(CommandManager.argument("id", IdentifierArgumentType.identifier())
							.then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
								.then(CommandManager.argument("color", StringArgumentType.word())
									.suggests((c, b) -> CommandSource.suggestMatching(Arrays.stream(DyeColor.values()).map(DyeColor::getName), b))
									.then(CommandManager.argument("name", StringArgumentType.greedyString())
										.executes(c -> execute(c, (s, w, p, sw, e, f) -> SurveyorCommands.addLandmark(w, p, sw, f, c.getArgument("id", Identifier.class), c.getArgument("pos", DefaultPosArgument.class).toAbsoluteBlockPos(c.getSource()), DyeColor.byName(c.getArgument("color", String.class), DyeColor.WHITE), c.getArgument("name", String.class), true)))
									)
								)
							)
						)
					)
				)
		);
	}

	public interface SurveyorCommandExecutor<T> {
		T execute(ServerSummary serverSummary, WorldSummary currentWorldSummary, ServerPlayerEntity player, ServerWorld world, SurveyorExploration exploration, Consumer<Text> feedback);
	}
}
