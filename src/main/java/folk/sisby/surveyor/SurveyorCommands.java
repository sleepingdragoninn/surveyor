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
import folk.sisby.surveyor.landmark.component.LandmarkComponentType;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.util.TextUtil;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.DefaultPosArgument;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.command.argument.ItemStackArgument;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.command.argument.TextArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class SurveyorCommands {
	private static final Multimap<UUID, UUID> requests = HashMultimap.create();

	public static MutableText prefix() {
		return Text.literal("").append(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED));
	}

	public static MutableText indent() {
		return Text.literal("").append(Text.literal("|| ").formatted(Formatting.DARK_RED));
	}

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
		feedback.accept(prefix().append(Text.literal("The server has global sharing enabled!").formatted(Formatting.YELLOW)));
		feedback.accept(prefix().append(Text.literal("You can't leave or modify the global sharing group!").formatted(Formatting.YELLOW)));
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
				summary.asMap(exploration).forEach((type, inner) -> inner.forEach((id, landmark) -> (landmark.owner().equals(WorldLandmarks.GLOBAL) ? landmarks : waypoints).add(landmark)));
				summary.asMap(groupExploration).forEach((type, inner) -> inner.forEach((id, landmark) -> (landmark.owner().equals(WorldLandmarks.GLOBAL) ? groupLandmarks : groupWaypoints).add(landmark)));
			}
		}
		feedback.accept(prefix().append(Text.literal("Map Exploration Summary:")));
		feedback.accept(
			indent()
				.append(Text.literal("You've explored ").formatted(Formatting.AQUA))
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
			indent()
				.append(Text.literal("You've explored ").formatted(Formatting.LIGHT_PURPLE))
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
			indent()
				.append(Text.literal("You've explored ").formatted(Formatting.GREEN))
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
			indent()
				.append(Text.literal("...and created ").formatted(Formatting.GREEN))
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
		return 1;
	}

	private static int share(ServerSummary serverSummary, ServerPlayerEntity player, Consumer<Text> feedback, String username) {
		ServerPlayerEntity sharePlayer = player.getServer().getPlayerManager().getPlayer(username);
		if (sharePlayer == null) {
			feedback.accept(prefix().append(Text.literal("Can't find an online player named ").formatted(Formatting.YELLOW)).append(Text.literal(username).formatted(Formatting.WHITE)).append(Text.literal(".").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (sharePlayer == player) {
			feedback.accept(prefix().append(Text.literal("You can't share map exploration with yourself!").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (requests.containsEntry(Surveyor.getUuid(player), Surveyor.getUuid(sharePlayer))) { // Accept Request
			if (serverSummary.groupSize(Surveyor.getUuid(player)) > 1 && serverSummary.groupSize(Surveyor.getUuid(sharePlayer)) > 1) {
				feedback.accept(prefix().append(Text.literal("You're in a group! leave your group first with:").formatted(Formatting.YELLOW)));
				feedback.accept(prefix().append(Text.literal("/surveyor unshare").formatted(Formatting.GOLD)));
				return 0;
			}
			requests.removeAll(Surveyor.getUuid(player)); // clear all other requests
			ServerSummary.of(player.getServer()).joinGroup(Surveyor.getUuid(player), Surveyor.getUuid(sharePlayer), player.getServer());
			feedback.accept(prefix().append(Text.literal("You're now sharing map exploration with ").formatted(Formatting.GREEN)).append(Text.literal("%d".formatted(serverSummary.groupSize(Surveyor.getUuid(player)) - 1)).formatted(Formatting.WHITE)).append(Text.literal((serverSummary.groupSize(Surveyor.getUuid(player)) - 1) > 1 ? " players:" : " player:").formatted(Formatting.GREEN)));
			feedback.accept(TextUtil.highlightStrings(serverSummary.groupPlayers(Surveyor.getUuid(player), player.getServer()).stream().map(PlayerSummary::username).filter(u -> !u.equals(player.getGameProfile().getName())).toList(), s -> Formatting.WHITE).formatted(Formatting.GREEN));
			for (ServerPlayerEntity friend : serverSummary.groupOtherServerPlayers(Surveyor.getUuid(player), player.getServer())) {
				friend.sendMessage(prefix().append(player.getDisplayName().copy().formatted(Formatting.WHITE)).append(Text.literal(" is now sharing their map with you.").formatted(Formatting.AQUA)));
				friend.sendMessage(prefix().append(Text.literal("You're now sharing map exploration with ").formatted(Formatting.AQUA)).append(Text.literal("%d".formatted(serverSummary.groupSize(Surveyor.getUuid(player)) - 1)).formatted(Formatting.WHITE)).append(Text.literal((serverSummary.groupSize(Surveyor.getUuid(player)) - 1) > 1 ? " players:" : " player:").formatted(Formatting.AQUA)));
				friend.sendMessage(TextUtil.highlightStrings(serverSummary.groupPlayers(Surveyor.getUuid(player), player.getServer()).stream().map(PlayerSummary::username).filter(u -> !u.equals(friend.getGameProfile().getName())).toList(), s -> Formatting.WHITE).formatted(Formatting.AQUA));
			}
			return 1;
		} else if (!requests.containsEntry(Surveyor.getUuid(sharePlayer), Surveyor.getUuid(player))) { // Make Request
			requests.put(Surveyor.getUuid(sharePlayer), Surveyor.getUuid(player));
			feedback.accept(prefix().append(Text.literal("Share request sent to ").formatted(Formatting.GREEN)).append(sharePlayer.getDisplayName().copy().formatted(Formatting.WHITE)).append(Text.literal(".").formatted(Formatting.GREEN)));
			sharePlayer.sendMessage(prefix().append(player.getDisplayName().copy().formatted(Formatting.WHITE)).append(Text.literal(" wants to share map exploration!").formatted(Formatting.AQUA)));
			if (serverSummary.groupSize(Surveyor.getUuid(player)) <= 1 && serverSummary.groupSize(Surveyor.getUuid(sharePlayer)) <= 1) { // Creating a group
				feedback.accept(prefix().append(Text.literal("If accepted, you'll share your map exploration.").formatted(Formatting.GREEN)));
				sharePlayer.sendMessage(prefix().append(Text.literal("To share your explored map area, enter:").formatted(Formatting.AQUA)));
			} else if (serverSummary.groupSize(Surveyor.getUuid(player)) <= 1) { // Joining their group
				feedback.accept(prefix().append(Text.literal("If accepted, you'll share with their group of ").formatted(Formatting.GREEN)).append(Text.literal("%d".formatted(serverSummary.groupSize(Surveyor.getUuid(sharePlayer)))).formatted(Formatting.WHITE)).append(Text.literal(".").formatted(Formatting.GREEN)));
				sharePlayer.sendMessage(prefix().append(Text.literal("To share your group of ").append(Text.literal("%d".formatted(serverSummary.groupSize(Surveyor.getUuid(sharePlayer)))).formatted(Formatting.WHITE)).formatted(Formatting.AQUA)).append(Text.literal(", enter:").formatted(Formatting.AQUA)));
			} else { // Sharing your group
				feedback.accept(prefix().append(Text.literal("If accepted, they'll share with your group of ").formatted(Formatting.GREEN)).append(Text.literal("%d".formatted(serverSummary.groupSize(Surveyor.getUuid(player)))).formatted(Formatting.WHITE)).append(Text.literal(".").formatted(Formatting.GREEN)));
				sharePlayer.sendMessage(prefix().append(Text.literal("To share with their group of ").append(Text.literal("%d".formatted(serverSummary.groupSize(Surveyor.getUuid(player)))).formatted(Formatting.WHITE)).formatted(Formatting.AQUA)).append(Text.literal(", enter:").formatted(Formatting.AQUA)));
			}
			sharePlayer.sendMessage(prefix().append(Text.literal("/surveyor share %s".formatted(player.getGameProfile().getName())).formatted(Formatting.GOLD)));
			return 1;
		} else {
			feedback.accept(prefix().append(Text.literal("You've already sent this player a share request!").formatted(Formatting.YELLOW)));
			return 0;
		}
	}

	private static int unshare(ServerSummary serverSummary, ServerPlayerEntity player, Consumer<Text> feedback) {
		int shareNumber = serverSummary.groupSize(Surveyor.getUuid(player)) - 1;
		if (shareNumber == 0) {
			feedback.accept(prefix().append(Text.literal("You're not sharing map exploration with anyone!").formatted(Formatting.YELLOW)));
			return 0;
		} else {
			Set<ServerPlayerEntity> friends = serverSummary.groupOtherServerPlayers(Surveyor.getUuid(player), player.getServer());
			ServerSummary.of(player.getServer()).leaveGroup(Surveyor.getUuid(player), player.getServer());
			feedback.accept(prefix().append(Text.literal("Stopped sharing map exploration with ").formatted(Formatting.GREEN)).append(Text.literal("%d".formatted(shareNumber)).formatted(Formatting.WHITE)).append(Text.literal(shareNumber > 1 ? " players." : " player.").formatted(Formatting.GREEN)));
			for (ServerPlayerEntity friend : friends) {
				int groupSize = serverSummary.groupSize(Surveyor.getUuid(friend)) - 1;
				friend.sendMessage(prefix().append(player.getDisplayName().copy().formatted(Formatting.WHITE)).append(Text.literal(" is no longer sharing with you.").formatted(Formatting.AQUA)));
				friend.sendMessage(prefix().append(Text.literal("You're now sharing map exploration with ").formatted(Formatting.AQUA)).append(Text.literal("%d".formatted(groupSize)).formatted(Formatting.WHITE)).append(Text.literal(groupSize == 0 ? " players." : groupSize > 1 ? " players:" : " player:").formatted(Formatting.AQUA)));
				if (groupSize > 0) friend.sendMessage(TextUtil.highlightStrings(serverSummary.groupPlayers(Surveyor.getUuid(friend), friend.getServer()).stream().map(PlayerSummary::username).filter(u -> !u.equals(friend.getGameProfile().getName())).toList(), s -> Formatting.WHITE).formatted(Formatting.AQUA));
			}
			return 1;
		}
	}

	private static int getLandmarks(WorldSummary summary, ServerPlayerEntity player, SurveyorExploration exploration, Consumer<Text> feedback, boolean global) {
		if (summary.landmarks() == null) {
			feedback.accept(prefix().append(Text.literal("The landmark system is dynamically disabled!").formatted(Formatting.YELLOW)));
			return 0;
		}
		Map<Identifier, Landmark> landmarks = summary.landmarks().asMap(global ? WorldLandmarks.GLOBAL : Surveyor.getUuid(player), player.hasPermissionLevel(2) ? null : exploration);
		if (landmarks.isEmpty()) {
			feedback.accept(prefix().append(Text.literal("There are no landmarks in this world!").formatted(Formatting.YELLOW)));
			return 0;
		}
		feedback.accept(prefix().append(Text.literal("World %s:".formatted(global ? "Landmarks" : "Waypoints"))));
		for (Landmark landmark : landmarks.values()) {
			feedback.accept(
				indent()
					.append(Text.literal("%s:".formatted(landmark.id().getNamespace())).formatted(Formatting.GRAY))
					.append(Text.literal(landmark.id().getPath()))
					.append(!landmark.components().contains(LandmarkComponentTypes.NAME) ? Text.of("") :
						Text.literal(": \"")
							.append(landmark.components().get(LandmarkComponentTypes.NAME).copy().styled(s -> s.withColor(landmark.components().contains(LandmarkComponentTypes.COLOR) ? landmark.components().get(LandmarkComponentTypes.COLOR) : Formatting.GREEN.getColorValue())))
							.append(Text.literal("\""))
					)
			);
		}
		return landmarks.size();
	}

	private static int viewLandmark(WorldSummary summary, ServerPlayerEntity player, ServerWorld world, Consumer<Text> feedback, Identifier id, boolean global) {
		if (summary.landmarks() == null) {
			feedback.accept(prefix().append(Text.literal("The landmark system is dynamically disabled!").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (!summary.landmarks().contains(global ? WorldLandmarks.GLOBAL : Surveyor.getUuid(player), id)) {
			feedback.accept(prefix().append(Text.literal("No landmark exists of that id!").formatted(Formatting.YELLOW)));
			return 0;
		}
		Landmark landmark = summary.landmarks().get(global ? WorldLandmarks.GLOBAL : Surveyor.getUuid(player), id);
		feedback.accept(prefix().append(Text.literal(landmark.owner().equals(WorldLandmarks.GLOBAL) ? "Landmark " : "Waypoint ").formatted(Formatting.GRAY)).append(Text.literal(id.toString())).append(Text.literal(": ")));
		landmark.toText().forEach(t -> feedback.accept(indent().append(t)));
		return 1;
	}

	private static int rawLandmark(WorldSummary summary, ServerPlayerEntity player, ServerWorld world, Consumer<Text> feedback, Identifier id, boolean global) {
		if (summary.landmarks() == null) {
			feedback.accept(prefix().append(Text.literal("The landmark system is dynamically disabled!").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (!summary.landmarks().contains(global ? WorldLandmarks.GLOBAL : Surveyor.getUuid(player), id)) {
			feedback.accept(prefix().append(Text.literal("No landmark exists of that id!").formatted(Formatting.YELLOW)));
			return 0;
		}
		Landmark landmark = summary.landmarks().get(global ? WorldLandmarks.GLOBAL : Surveyor.getUuid(player), id);
		feedback.accept(prefix().append(Text.literal(landmark.owner().equals(WorldLandmarks.GLOBAL) ? "Landmark " : "Waypoint ").formatted(Formatting.GRAY)).append(Text.literal(id.toString())).append(Text.literal(": ")).append(Text.literal(landmark.toNbt().toString()).formatted(Formatting.AQUA)));
		return 1;
	}

	private static int removeLandmark(WorldSummary summary, ServerPlayerEntity player, ServerWorld world, Consumer<Text> feedback, Identifier id, boolean global) {
		if (summary.landmarks() == null) {
			feedback.accept(prefix().append(Text.literal("The landmark system is dynamically disabled!").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (!summary.landmarks().contains(global ? WorldLandmarks.GLOBAL : Surveyor.getUuid(player), id)) {
			feedback.accept(prefix().append(Text.literal("No landmark exists of that id!").formatted(Formatting.YELLOW)));
			return 0;
		}
		Landmark landmark = summary.landmarks().get(global ? WorldLandmarks.GLOBAL : Surveyor.getUuid(player), id);
		if ((landmark.owner().equals(WorldLandmarks.GLOBAL) || landmark.owner() != Surveyor.getUuid(player)) && !player.hasPermissionLevel(2)) {
			feedback.accept(prefix().append(Text.literal("You don't have permission to delete that landmark!").formatted(Formatting.YELLOW)));
			return 0;
		}
		summary.landmarks().remove(world, global ? WorldLandmarks.GLOBAL : Surveyor.getUuid(player), id);
		feedback.accept(prefix().append(Text.literal("%s removed successfully!".formatted(landmark.owner().equals(WorldLandmarks.GLOBAL) ? "Landmark" : "Waypoint")).formatted(Formatting.GREEN)));
		return 1;
	}

	private static int trimLandmark(WorldSummary summary, ServerPlayerEntity player, ServerWorld world, Consumer<Text> feedback, Identifier id, Identifier componentType, boolean global) {
		if (summary.landmarks() == null) {
			feedback.accept(prefix().append(Text.literal("The landmark system is dynamically disabled!").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (!summary.landmarks().contains(global ? WorldLandmarks.GLOBAL : Surveyor.getUuid(player), id)) {
			feedback.accept(prefix().append(Text.literal("No landmark exists of that id!").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (!LandmarkComponentType.containsType(componentType)) {
			feedback.accept(prefix().append(Text.literal("No component exists of that id!").formatted(Formatting.YELLOW)));
			return 0;
		}
		Landmark landmark = summary.landmarks().get(global ? WorldLandmarks.GLOBAL : Surveyor.getUuid(player), id);
		if ((landmark.owner().equals(WorldLandmarks.GLOBAL) || landmark.owner() != Surveyor.getUuid(player)) && !player.hasPermissionLevel(2)) {
			feedback.accept(prefix().append(Text.literal("You don't have permission to modify that landmark!").formatted(Formatting.YELLOW)));
			return 0;
		}
		landmark.components().remove(LandmarkComponentType.getType(componentType));
		summary.landmarks().put(world, landmark);
		feedback.accept(prefix().append(Text.literal("%s trimmed successfully!".formatted(landmark.owner().equals(WorldLandmarks.GLOBAL) ? "Landmark" : "Waypoint")).formatted(Formatting.GREEN)));
		return 1;
	}

	private static int appendColor(WorldSummary summary, ServerPlayerEntity player, ServerWorld world, Consumer<Text> feedback, Identifier type, String colorString, boolean global) {
		if (summary.landmarks() == null) {
			feedback.accept(prefix().append(Text.literal("The landmark system is dynamically disabled!").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (!summary.landmarks().contains(global ? WorldLandmarks.GLOBAL : Surveyor.getUuid(player), type)) {
			feedback.accept(prefix().append(Text.literal("No landmark exists of that id!").formatted(Formatting.YELLOW)));
			return 0;
		}
		Landmark landmark = summary.landmarks().get(global ? WorldLandmarks.GLOBAL : Surveyor.getUuid(player), type);
		if ((landmark.owner().equals(WorldLandmarks.GLOBAL) || landmark.owner() != Surveyor.getUuid(player)) && !player.hasPermissionLevel(2)) {
			feedback.accept(prefix().append(Text.literal("You don't have permission to modify that landmark!").formatted(Formatting.YELLOW)));
			return 0;
		}
		TextColor color = TextColor.parse(colorString);
		if (color == null) {
			feedback.accept(prefix().append(Text.literal("Not a valid color! Use color names or hex codes").formatted(Formatting.YELLOW)));
			return 0;
		}
		landmark.components().set(LandmarkComponentTypes.COLOR, color.getRgb());
		summary.landmarks().put(world, landmark);
		feedback.accept(prefix().append(Text.literal("%s appended successfully!".formatted(landmark.owner().equals(WorldLandmarks.GLOBAL) ? "Landmark" : "Waypoint")).formatted(Formatting.GREEN)));
		return 1;
	}

	private static int addBlockLandmark(WorldSummary summary, ServerPlayerEntity player, ServerWorld world, Consumer<Text> feedback, BlockPos pos, boolean global) {
		if (summary.landmarks() == null) {
			feedback.accept(prefix().append(Text.literal("The landmark system is dynamically disabled!").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (global && !player.hasPermissionLevel(2)) {
			feedback.accept(prefix().append(Text.literal("You don't have permission to add that landmark!").formatted(Formatting.YELLOW)));
			return 0;
		}
		Identifier id = Surveyor.id("block/%s/%s/%s".formatted(pos.getX(), pos.getY(), pos.getZ()));
		if (summary.landmarks().contains(global ? WorldLandmarks.GLOBAL : Surveyor.getUuid(player), id)) {
			feedback.accept(prefix().append(Text.literal("A landmark with this ID already exists! Replacing...").formatted(Formatting.YELLOW)));
		}
		summary.landmarks().put(world, Landmark.create(global ? WorldLandmarks.GLOBAL : Surveyor.getUuid(player), id, builder -> LandmarkComponentTypes.forBlock(builder, world, pos)));
		feedback.accept(prefix().append(Text.literal("Added new %s %s!".formatted(global ? "Landmark" : "Waypoint", id)).formatted(Formatting.GREEN)));
		return 1;
	}

	private static int addIdLandmark(WorldSummary summary, ServerPlayerEntity player, ServerWorld world, Consumer<Text> feedback, Identifier id, BlockPos pos, ItemStackArgument stack, Text name, Text lore, boolean global) throws CommandSyntaxException {
		ItemStack icon = stack.createStack(1, false);
		if (summary.landmarks() == null) {
			feedback.accept(prefix().append(Text.literal("The landmark system is dynamically disabled!").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (global && !player.hasPermissionLevel(2)) {
			feedback.accept(prefix().append(Text.literal("You don't have permission to add that landmark!").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (summary.landmarks().contains(global ? WorldLandmarks.GLOBAL : Surveyor.getUuid(player), id)) {
			feedback.accept(prefix().append(Text.literal("A landmark with this ID already exists! Replacing...").formatted(Formatting.YELLOW)));
		}
		summary.landmarks().put(world, Landmark.create(global ? WorldLandmarks.GLOBAL : Surveyor.getUuid(player), id, builder -> builder
			.add(LandmarkComponentTypes.POS, pos)
			.add(LandmarkComponentTypes.STACK, icon)
			.add(LandmarkComponentTypes.NAME, name)
			.add(LandmarkComponentTypes.LORE, List.of(lore))
		));
		feedback.accept(prefix().append(Text.literal("Added new %s %s!".formatted(global ? "Landmark" : "Waypoint", id)).formatted(Formatting.GREEN)));
		return 1;
	}

	private static int addIdLandmark(WorldSummary summary, ServerPlayerEntity player, ServerWorld world, Consumer<Text> feedback, Identifier id, BlockPos pos, ItemStackArgument stack, Text name, boolean global) throws CommandSyntaxException {
		ItemStack icon = stack.createStack(1, false);
		if (summary.landmarks() == null) {
			feedback.accept(prefix().append(Text.literal("The landmark system is dynamically disabled!").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (global && !player.hasPermissionLevel(2)) {
			feedback.accept(prefix().append(Text.literal("You don't have permission to add that landmark!").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (summary.landmarks().contains(global ? WorldLandmarks.GLOBAL : Surveyor.getUuid(player), id)) {
			feedback.accept(prefix().append(Text.literal("A landmark with this ID already exists! Replacing...").formatted(Formatting.YELLOW)));
		}
		summary.landmarks().put(world, Landmark.create(global ? WorldLandmarks.GLOBAL : Surveyor.getUuid(player), id, builder -> builder
			.add(LandmarkComponentTypes.POS, pos)
			.add(LandmarkComponentTypes.STACK, icon)
			.add(LandmarkComponentTypes.NAME, name)
		));
		feedback.accept(prefix().append(Text.literal("Added new %s %s!".formatted(global ? "Landmark" : "Waypoint", id)).formatted(Formatting.GREEN)));
		return 1;
	}

	public static <T> T map(CommandContext<ServerCommandSource> context, SurveyorCommandExecutor<T> executor, boolean feedback) {
		ServerPlayerEntity player;
		try {
			player = context.getSource().getPlayerOrThrow();
		} catch (CommandSyntaxException e) {
			if (feedback) Surveyor.LOGGER.error("[Surveyor] Commands cannot be invoked by a non-player");
			return null;
		}

		SurveyorExploration exploration = SurveyorExploration.of(player);
		try {
			return executor.execute(ServerSummary.of(player.getServer()), WorldSummary.of(context.getSource().getWorld()), player, context.getSource().getWorld(), exploration, t -> context.getSource().sendFeedback(() -> t, false));
		} catch (Exception e) {
			if (feedback) context.getSource().sendFeedback(() -> Text.literal("Command failed! Check log for details.").formatted(Formatting.RED), false);
			if (feedback) Surveyor.LOGGER.error("[Surveyor] Error while executing command: {}", context.getInput(), e);
			return null;
		}
	}

	public static int execute(CommandContext<ServerCommandSource> context, SurveyorCommandExecutor<Integer> executor) {
		return Objects.requireNonNullElse(map(context, executor, true), 0);
	}

	public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
		dispatcher.register(
			CommandManager.literal("surveyor")
				.executes(c -> execute(c, (s, w, p, sw, e, f) -> info(s, p, e, f)))
				.then(Surveyor.CONFIG.networking.globalSharing ?
					CommandManager.literal("share")
						.executes(c -> execute(c, (s, w, p, sw, e, f) -> informGlobal(s, p, f))) :
					CommandManager.literal("share")
						.then(CommandManager.argument("player", StringArgumentType.word())
							.suggests((c, b) -> CommandSource.suggestMatching(c.getSource().getServer().getPlayerManager().getPlayerList().stream().filter(p -> c.getSource().getPlayer() != p).map(p -> p.getGameProfile().getName()), b))
							.executes(c -> execute(c, (s, w, p, sw, e, f) -> share(s, p, f, c.getArgument("player", String.class))))
						)
				).then(Surveyor.CONFIG.networking.globalSharing ?
					CommandManager.literal("unshare")
						.executes(c -> execute(c, (s, w, p, sw, e, f) -> informGlobal(s, p, f))) :
					CommandManager.literal("unshare")
						.executes(c -> execute(c, (s, w, p, sw, e, f) -> unshare(s, p, f)))
				)
		);
		dispatcher.register(
			CommandManager.literal("landmarks")
				.requires(c -> Surveyor.CONFIG.landmarks != SystemMode.DISABLED)
				.requires(c -> c.getPlayer() == null || c.getPlayer().hasPermissionLevel(2))
				.executes(c -> execute(c, (s, w, p, sw, e, f) -> getLandmarks(w, p, e, f, true)))
				.then(CommandManager.literal("new")
					.requires(c -> Surveyor.CONFIG.landmarks != SystemMode.FROZEN)
					.then(CommandManager.literal("block")
						.then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
							.executes(c -> execute(c, (s, w, p, sw, e, f) -> addBlockLandmark(w, p, sw, f, c.getArgument("pos", DefaultPosArgument.class).toAbsoluteBlockPos(c.getSource()), true)))
						)
					)
					.then(CommandManager.literal("id")
						.then(CommandManager.argument("id", IdentifierArgumentType.identifier())
							.then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
								.then(CommandManager.argument("icon", ItemStackArgumentType.itemStack(registryAccess))
									.then(CommandManager.argument("name", TextArgumentType.text(registryAccess))
										.then(CommandManager.argument("lore", TextArgumentType.text(registryAccess))
											.executes(c -> execute(c, (s, w, p, sw, e, f) -> {
												try {
													return addIdLandmark(w, p, sw, f, c.getArgument("id", Identifier.class), c.getArgument("pos", DefaultPosArgument.class).toAbsoluteBlockPos(c.getSource()), ItemStackArgumentType.getItemStackArgument(c, "icon"), c.getArgument("name", Text.class), c.getArgument("lore", Text.class), true);
												} catch (CommandSyntaxException ex) {
													throw new RuntimeException(ex);
												}
											}))
										)
									)
								)
							)
						)
					)
				)
				.then(CommandManager.literal("append")
					.then(CommandManager.argument("id", IdentifierArgumentType.identifier())
						.suggests((c, b) -> CommandSource.suggestIdentifiers((Iterable<Identifier>) map(c, (s, w, p, sw, e, f) -> w.landmarks() == null ? new HashSet<Identifier>() : w.landmarks().asMap(WorldLandmarks.GLOBAL, p.hasPermissionLevel(2) ? null : e).keySet(), false), b))
						.then(CommandManager.literal("surveyor:color")
							.then(CommandManager.argument("color", StringArgumentType.greedyString())
								.suggests((c, s) -> CommandSource.suggestMatching(Formatting.getNames(true, false), s))
								.executes(c -> execute(c, (s, w, p, sw, e, f) -> appendColor(w, p, sw, f, c.getArgument("id", Identifier.class), c.getArgument("color", String.class), true)))
							)
						)
					)
				)
				.then(CommandManager.literal("trim")
					.then(CommandManager.argument("id", IdentifierArgumentType.identifier())
						.suggests((c, b) -> CommandSource.suggestIdentifiers((Iterable<Identifier>) map(c, (s, w, p, sw, e, f) -> w.landmarks() == null ? new HashSet<Identifier>() : w.landmarks().asMap(WorldLandmarks.GLOBAL, p.hasPermissionLevel(2) ? null : e).keySet(), false), b))
						.then(CommandManager.argument("component", IdentifierArgumentType.identifier())
							.suggests((c, b) -> CommandSource.suggestIdentifiers(LandmarkComponentType.keySet(), b))
							.executes(c -> execute(c, (s, w, p, sw, e, f) -> trimLandmark(w, p, sw, f, c.getArgument("id", Identifier.class), c.getArgument("component", Identifier.class), true)))
						)
					)
				)
				.then(CommandManager.literal("view")
					.then(CommandManager.argument("id", IdentifierArgumentType.identifier())
						.suggests((c, b) -> CommandSource.suggestIdentifiers((Iterable<Identifier>) map(c, (s, w, p, sw, e, f) -> w.landmarks() == null ? new HashSet<Identifier>() : w.landmarks().asMap(WorldLandmarks.GLOBAL, p.hasPermissionLevel(2) ? null : e).keySet(), false), b))
						.executes(c -> execute(c, (s, w, p, sw, e, f) -> viewLandmark(w, p, sw, f, c.getArgument("id", Identifier.class), true)))
					)
				)
				.then(CommandManager.literal("raw")
					.requires(c -> Surveyor.CONFIG.debugCommands)
					.then(CommandManager.argument("id", IdentifierArgumentType.identifier())
						.suggests((c, b) -> CommandSource.suggestIdentifiers((Iterable<Identifier>) map(c, (s, w, p, sw, e, f) -> w.landmarks() == null ? new HashSet<Identifier>() : w.landmarks().asMap(WorldLandmarks.GLOBAL, p.hasPermissionLevel(2) ? null : e).keySet(), false), b))
						.executes(c -> execute(c, (s, w, p, sw, e, f) -> rawLandmark(w, p, sw, f, c.getArgument("id", Identifier.class), true)))
					)
				)
				.then(CommandManager.literal("remove")
					.requires(c -> Surveyor.CONFIG.landmarks != SystemMode.FROZEN)
					.then(CommandManager.argument("id", IdentifierArgumentType.identifier())
						.suggests((c, b) -> CommandSource.suggestIdentifiers((Iterable<Identifier>) map(c, (s, w, p, sw, e, f) -> w.landmarks() == null ? new HashSet<Identifier>() : w.landmarks().asMap(WorldLandmarks.GLOBAL, p.hasPermissionLevel(2) ? null : e).keySet(), false), b))
						.executes(c -> execute(c, (s, w, p, sw, e, f) -> removeLandmark(w, p, sw, f, c.getArgument("id", Identifier.class), true)))
					)
				)
		);
		dispatcher.register(
			CommandManager.literal("waypoints")
				.requires(c -> Surveyor.CONFIG.landmarks != SystemMode.DISABLED)
				.executes(c -> execute(c, (s, w, p, sw, e, f) -> getLandmarks(w, p, e, f, false)))
				.then(CommandManager.literal("new")
					.requires(c -> Surveyor.CONFIG.landmarks != SystemMode.FROZEN)
					.then(CommandManager.literal("block")
						.then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
							.executes(c -> execute(c, (s, w, p, sw, e, f) -> addBlockLandmark(w, p, sw, f, c.getArgument("pos", DefaultPosArgument.class).toAbsoluteBlockPos(c.getSource()), false)))
						)
					)
				)
				.then(CommandManager.literal("view")
					.then(CommandManager.argument("id", IdentifierArgumentType.identifier())
						.suggests((c, b) -> CommandSource.suggestIdentifiers((Iterable<Identifier>) map(c, (s, w, p, sw, e, f) -> w.landmarks() == null ? new HashSet<Identifier>() : w.landmarks().asMap(Surveyor.getUuid(p), p.hasPermissionLevel(2) ? null : e).keySet(), false), b))
						.executes(c -> execute(c, (s, w, p, sw, e, f) -> viewLandmark(w, p, sw, f, c.getArgument("id", Identifier.class), false)))
					)
				)
				.then(CommandManager.literal("raw")
					.requires(c -> Surveyor.CONFIG.debugCommands)
					.then(CommandManager.argument("id", IdentifierArgumentType.identifier())
						.suggests((c, b) -> CommandSource.suggestIdentifiers((Iterable<Identifier>) map(c, (s, w, p, sw, e, f) -> w.landmarks() == null ? new HashSet<Identifier>() : w.landmarks().asMap(Surveyor.getUuid(p), p.hasPermissionLevel(2) ? null : e).keySet(), false), b))
						.executes(c -> execute(c, (s, w, p, sw, e, f) -> rawLandmark(w, p, sw, f, c.getArgument("id", Identifier.class), false)))
					)
				)
				.then(CommandManager.literal("remove")
					.requires(c -> Surveyor.CONFIG.landmarks != SystemMode.FROZEN)
					.then(CommandManager.argument("id", IdentifierArgumentType.identifier())
						.suggests((c, b) -> CommandSource.suggestIdentifiers((Iterable<Identifier>) map(c, (s, w, p, sw, e, f) -> w.landmarks() == null ? new HashSet<Identifier>() : w.landmarks().asMap(Surveyor.getUuid(p), p.hasPermissionLevel(2) ? null : e).keySet(), false), b))
						.executes(c -> execute(c, (s, w, p, sw, e, f) -> removeLandmark(w, p, sw, f, c.getArgument("id", Identifier.class), false)))
					)
				)
		);
	}

	public interface SurveyorCommandExecutor<T> {
		T execute(ServerSummary serverSummary, WorldSummary currentWorldSummary, ServerPlayerEntity player, ServerWorld world, SurveyorExploration exploration, Consumer<Text> feedback);
	}
}
