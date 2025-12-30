package folk.sisby.surveyor;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.DataResult;
import folk.sisby.surveyor.config.NetworkMode;
import folk.sisby.surveyor.config.SystemMode;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentType;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.structure.WorldStructures;
import folk.sisby.surveyor.terrain.WorldTerrain;
import folk.sisby.surveyor.util.TextUtil;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.DefaultPosArgument;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.command.argument.ItemStackArgument;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.command.argument.TextArgumentType;
import net.minecraft.command.argument.UuidArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.text.Texts;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.apache.commons.lang3.text.WordUtils;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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

	private static boolean playerMissing(@Nullable ServerPlayerEntity player, Consumer<Text> feedback) {
		if (player == null) {
			feedback.accept(prefix().append(Text.literal("Can't run this command as a non-player!")));
			return true;
		}
		return false;
	}

	private static int informGlobal(MinecraftServer server, @Nullable ServerPlayerEntity player, @Nullable SurveyorExploration exploration, Consumer<Text> feedback) {
		feedback.accept(prefix().append(Text.literal("The server has global sharing enabled!").formatted(Formatting.YELLOW)));
		feedback.accept(prefix().append(Text.literal("You can't leave or modify the global sharing group!").formatted(Formatting.YELLOW)));
		if (playerMissing(player, feedback)) return 0;
		informGroup(player, ServerSummary.of(server).groupPlayers(Surveyor.getUuid(player)), feedback);
		return 0;
	}

	private static int info(MinecraftServer server, @Nullable ServerPlayerEntity player, @Nullable SurveyorExploration exploration, Consumer<Text> feedback) {
		Set<PlayerSummary> group = player == null ? null : ServerSummary.of(server).groupPlayers(Surveyor.getUuid(player));
		SurveyorExploration groupExploration = player == null ? null : SurveyorExploration.ofShared(player);
		Set<Landmark> landmarks = new HashSet<>();
		Set<Landmark> waypoints = new HashSet<>();
		Set<Landmark> groupLandmarks = new HashSet<>();
		Set<Landmark> groupWaypoints = new HashSet<>();
		int chunks = exploration == null ? 0 : exploration.chunkCount();
		int structures = exploration == null ? 0 : exploration.structureCount();
		for (ServerWorld world : server.getWorlds()) {
			WorldLandmarks worldLandmarks = WorldSummary.of(world).landmarks();
			if (worldLandmarks != null) {
				worldLandmarks.asMap(exploration).values().forEach(landmark -> (landmark.owner().equals(WorldLandmarks.GLOBAL) ? landmarks : waypoints).add(landmark));
				if (groupExploration != null) worldLandmarks.asMap(groupExploration).values().forEach(landmark -> (landmark.owner().equals(WorldLandmarks.GLOBAL) ? groupLandmarks : groupWaypoints).add(landmark));
			}
			if (exploration == null) {
				WorldTerrain terrainSummary = WorldSummary.of(world).terrain();
				WorldStructures structureSummary = WorldSummary.of(world).structures();
				if (terrainSummary != null) chunks += terrainSummary.bitSet(null).values().stream().mapToInt(BitSet::cardinality).sum();
				if (structureSummary != null) structures += structureSummary.keySet(null).size();
			}
		}
		feedback.accept(prefix().append(Text.literal(player == null ? "Surveyor Data Summary:" : "Map Exploration Summary:")));
		feedback.accept(
			indent()
				.append(Text.literal(player == null ? "Mapped " : "You've explored ").formatted(Formatting.AQUA))
				.append(Text.literal("%d".formatted(chunks)).formatted(Formatting.WHITE))
				.append(Text.literal(" total chunks!").formatted(Formatting.AQUA))
				.append(
					group == null || group.size() <= 1 ? Text.empty() :
						Text.literal(" (").formatted(Formatting.AQUA)
							.append(Text.literal("%d".formatted(groupExploration.chunkCount())).formatted(Formatting.WHITE))
							.append(Text.literal(" with friends)").formatted(Formatting.AQUA))
				)
		);
		feedback.accept(
			indent()
				.append(Text.literal(player == null ? "Mapped " : "You've discovered ").formatted(Formatting.LIGHT_PURPLE))
				.append(Text.literal("%d".formatted(structures)).formatted(Formatting.WHITE))
				.append(Text.literal(" structures!").formatted(Formatting.LIGHT_PURPLE))
				.append(
					group == null || group.size() <= 1 ? Text.empty() :
						Text.literal(" (").formatted(Formatting.LIGHT_PURPLE)
							.append(Text.literal("%d".formatted(groupExploration.structureCount())).formatted(Formatting.WHITE))
							.append(Text.literal(" with friends)").formatted(Formatting.LIGHT_PURPLE))
				)
		);
		feedback.accept(
			indent()
				.append(Text.literal(player == null ? "Mapped " : "You've discovered ").formatted(Formatting.GREEN))
				.append(Text.literal("%d".formatted(landmarks.size())).formatted(Formatting.WHITE))
				.append(Text.literal(" landmarks!").formatted(Formatting.GREEN))
				.append(
					group == null || group.size() <= 1 ? Text.empty() :
						Text.literal(" (").formatted(Formatting.GREEN)
							.append(Text.literal("%d".formatted(groupLandmarks.size())).formatted(Formatting.WHITE))
							.append(Text.literal(" with friends)").formatted(Formatting.GREEN))
				)
		);
		feedback.accept(
			indent()
				.append(Text.literal(player == null ? "Recorded " : "...and created ").formatted(Formatting.GREEN))
				.append(Text.literal("%d".formatted(waypoints.size())).formatted(Formatting.WHITE))
				.append(Text.literal(" waypoints!").formatted(Formatting.GREEN))
				.append(
					group == null || group.size() <= 1 ? Text.empty() :
						Text.literal(" (").formatted(Formatting.GREEN)
							.append(Text.literal("%d".formatted(groupWaypoints.size())).formatted(Formatting.WHITE))
							.append(Text.literal(" with friends)").formatted(Formatting.GREEN))
				)
		);
		if (group != null && group.size() > 1) {
			informGroup(player, group, feedback);
		}
		return 1;
	}

	private static int share(MinecraftServer server, @Nullable ServerPlayerEntity player, Consumer<Text> feedback, String username) {
		if (playerMissing(player, feedback)) return 0;
		ServerSummary serverSummary = ServerSummary.of(server);
		ServerPlayerEntity sharePlayer = server.getPlayerManager().getPlayer(username);
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
			ServerSummary.of(player.getServer()).joinGroup(Surveyor.getUuid(player), Surveyor.getUuid(sharePlayer));
			feedback.accept(prefix().append(Text.literal("You're now sharing map exploration with ").formatted(Formatting.GREEN)).append(Text.literal("%d".formatted(serverSummary.groupSize(Surveyor.getUuid(player)) - 1)).formatted(Formatting.WHITE)).append(Text.literal((serverSummary.groupSize(Surveyor.getUuid(player)) - 1) > 1 ? " players:" : " player:").formatted(Formatting.GREEN)));
			feedback.accept(TextUtil.highlightStrings(serverSummary.groupPlayers(Surveyor.getUuid(player)).stream().map(PlayerSummary::username).filter(u -> !u.equals(player.getGameProfile().getName())).toList(), s -> Formatting.WHITE).formatted(Formatting.GREEN));
			for (ServerPlayerEntity friend : serverSummary.getSharingPlayers(Surveyor.getUuid(player), NetworkMode.GROUP, false)) {
				friend.sendMessage(prefix().append(player.getDisplayName().copy().formatted(Formatting.WHITE)).append(Text.literal(" is now sharing their map with you.").formatted(Formatting.AQUA)));
				friend.sendMessage(prefix().append(Text.literal("You're now sharing map exploration with ").formatted(Formatting.AQUA)).append(Text.literal("%d".formatted(serverSummary.groupSize(Surveyor.getUuid(player)) - 1)).formatted(Formatting.WHITE)).append(Text.literal((serverSummary.groupSize(Surveyor.getUuid(player)) - 1) > 1 ? " players:" : " player:").formatted(Formatting.AQUA)));
				friend.sendMessage(TextUtil.highlightStrings(serverSummary.groupPlayers(Surveyor.getUuid(player)).stream().map(PlayerSummary::username).filter(u -> !u.equals(friend.getGameProfile().getName())).toList(), s -> Formatting.WHITE).formatted(Formatting.AQUA));
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

	private static int unshare(MinecraftServer server, @Nullable ServerPlayerEntity player, @Nullable SurveyorExploration exploration, Consumer<Text> feedback) {
		if (playerMissing(player, feedback)) return 0;
		ServerSummary serverSummary = ServerSummary.of(server);
		int shareNumber = serverSummary.groupSize(Surveyor.getUuid(player)) - 1;
		if (shareNumber == 0) {
			feedback.accept(prefix().append(Text.literal("You're not sharing map exploration with anyone!").formatted(Formatting.YELLOW)));
			return 0;
		} else {
			Set<ServerPlayerEntity> friends = serverSummary.getSharingPlayers(Surveyor.getUuid(player), NetworkMode.GROUP, false);
			ServerSummary.of(player.getServer()).leaveGroup(Surveyor.getUuid(player));
			feedback.accept(prefix().append(Text.literal("Stopped sharing map exploration with ").formatted(Formatting.GREEN)).append(Text.literal("%d".formatted(shareNumber)).formatted(Formatting.WHITE)).append(Text.literal(shareNumber > 1 ? " players." : " player.").formatted(Formatting.GREEN)));
			for (ServerPlayerEntity friend : friends) {
				int groupSize = serverSummary.groupSize(Surveyor.getUuid(friend)) - 1;
				friend.sendMessage(prefix().append(player.getDisplayName().copy().formatted(Formatting.WHITE)).append(Text.literal(" is no longer sharing with you.").formatted(Formatting.AQUA)));
				friend.sendMessage(prefix().append(Text.literal("You're now sharing map exploration with ").formatted(Formatting.AQUA)).append(Text.literal("%d".formatted(groupSize)).formatted(Formatting.WHITE)).append(Text.literal(groupSize == 0 ? " players." : groupSize > 1 ? " players:" : " player:").formatted(Formatting.AQUA)));
				if (groupSize > 0) friend.sendMessage(TextUtil.highlightStrings(serverSummary.groupPlayers(Surveyor.getUuid(friend)).stream().map(PlayerSummary::username).filter(u -> !u.equals(friend.getGameProfile().getName())).toList(), s -> Formatting.WHITE).formatted(Formatting.AQUA));
			}
			return 1;
		}
	}

	private static int getLandmarks(MinecraftServer server, @Nullable ServerPlayerEntity player, Consumer<Text> feedback, boolean global) {
		Map<Identifier, Collection<Landmark>> dimensionLandmarks = new LinkedHashMap<>();
		boolean op = player == null || player.hasPermissionLevel(2);
		for (ServerWorld world : server.getWorlds()) {
			WorldSummary summary = WorldSummary.of(world);
			if (summary.landmarks() == null) {
				feedback.accept(prefix().append(Text.literal("The landmark system is dynamically disabled!").formatted(Formatting.YELLOW)));
				return 0;
			}
			Table<UUID, Identifier, Landmark> landmarks = summary.landmarks().asMap(op ? null : Surveyor.CONFIG.networking.waypoints.atLeast(NetworkMode.GROUP) ? SurveyorExploration.ofShared(player) : SurveyorExploration.of(player));
			if (global) {
				if (landmarks.containsRow(WorldLandmarks.GLOBAL)) {
					dimensionLandmarks.put(world.getRegistryKey().getValue(), landmarks.row(WorldLandmarks.GLOBAL).values());
				}
			} else {
				landmarks.rowKeySet().remove(WorldLandmarks.GLOBAL);
				dimensionLandmarks.put(world.getRegistryKey().getValue(), landmarks.values());
			}
		}
		int numLandmarks = dimensionLandmarks.values().stream().mapToInt(Collection::size).sum();
		feedback.accept(prefix().append(Text.literal("%s %d %s:".formatted(op ? "There are" : "You've discovered", numLandmarks, global ? "Landmarks" : "Waypoints"))));
		for (Identifier dimension : dimensionLandmarks.keySet()) {
			Collection<Landmark> landmarks = dimensionLandmarks.get(dimension);
			if (!landmarks.isEmpty()) feedback.accept(indent().append(indent()).append(Text.literal("%s:".formatted(WordUtils.capitalize(dimension.getPath().replaceAll("[/_-]", " "))))));
			for (Landmark landmark : landmarks) {
				Text idText = Text.empty().append(Text.literal("%s:".formatted(landmark.id().getNamespace())).formatted(Formatting.GRAY)).append(Text.literal(landmark.id().getPath()));
				Integer color = landmark.get(LandmarkComponentTypes.COLOR);
				String command = global ? "/landmarks view %s %s".formatted(dimension, landmark.id()) : "/waypoints view %s %s%s".formatted(dimension, landmark.id(), player != null && landmark.owner().equals(Surveyor.getUuid(player)) ? "" : " " + landmark.owner());
				feedback.accept(
					indent()
						.append(player == null || global ? Text.empty() : Text.literal("%s | ".formatted(Optional.ofNullable(ServerSummary.of(server).getPlayer(landmark.owner())).map(PlayerSummary::username).orElse(landmark.owner().toString()))).formatted(Formatting.GRAY))
						.append(player == null && landmark.contains(LandmarkComponentTypes.NAME) ? idText.copy().append(" ") : Text.empty())
						.append((landmark.contains(LandmarkComponentTypes.NAME) ? Text.literal("\"").append(landmark.get(LandmarkComponentTypes.NAME)).append("\"") : idText).copy().styled(s -> s
							.withColor(color == null ? 0xFFFFFF : 0xFFFFFF & color)
							.withHoverEvent(player == null ? null : new HoverEvent.ShowText(Text.empty()
								.append(global || landmark.owner().equals(Surveyor.getUuid(player)) ? Text.empty() : Text.empty().append(Text.literal("owner: ").formatted(Formatting.AQUA)).append(landmark.owner().toString()).append("\n"))
								.append(Text.literal("id: ").formatted(Formatting.AQUA)).append(idText).append("\n")
								.append(Texts.join(landmark.toText(), Text.of("\n"))).append("\n").append(Text.literal(command).formatted(Formatting.AQUA))))
							.withClickEvent(new ClickEvent.RunCommand(command)))
						)
				);
			}
		}
		return numLandmarks;
	}

	private static int viewLandmark(@Nullable ServerPlayerEntity player, Consumer<Text> feedback, ServerWorld world, UUID owner, Identifier id, boolean raw) {
		WorldSummary summary = WorldSummary.of(world);
		if (summary.landmarks() == null) {
			feedback.accept(prefix().append(Text.literal("The landmark system is dynamically disabled!").formatted(Formatting.YELLOW)));
			return 0;
		}
		Landmark landmark = summary.landmarks().get(owner, id);
		if (landmark == null) {
			feedback.accept(prefix().append(Text.literal("No landmark exists of that id!").formatted(Formatting.YELLOW)));
			return 0;
		}
		String command = landmark.owner().equals(WorldLandmarks.GLOBAL) ? "/landmarks remove %s %s".formatted(world.getRegistryKey().getValue(), landmark.id()) : "/waypoints remove %s %s%s".formatted(world.getRegistryKey().getValue(), landmark.id(), (player == null || !landmark.owner().equals(Surveyor.getUuid(player)) ? " " + landmark.owner() : ""));
		feedback.accept(prefix()
			.append(Text.literal(owner.equals(WorldLandmarks.GLOBAL) ? "Landmark " : "Waypoint ").formatted(Formatting.GRAY))
			.append(Text.literal(id.toString()))
			.append(Text.literal(" "))
			.append(!Surveyor.canModify(landmark.owner(), player) ? Text.empty() : Text.empty()
				.append(Text.literal("<").formatted(Formatting.GRAY))
				.append(Text.literal("remove").formatted(Formatting.AQUA).styled(s -> s
					.withHoverEvent(new HoverEvent.ShowText(Text.literal(command).formatted(Formatting.AQUA)))
					.withClickEvent(new ClickEvent.RunCommand(command))
				))
				.append(Text.literal(">").formatted(Formatting.GRAY))
			)
		);
		if (raw) {
			feedback.accept(indent().append(Text.literal(landmark.toNbt().toString())));
		} else {
			landmark.toText().forEach(t -> feedback.accept(indent().append(t)));
		}
		return 1;
	}

	private static int removeLandmark(@Nullable ServerPlayerEntity player, Consumer<Text> feedback, ServerWorld world, UUID owner, Identifier id) {
		WorldSummary summary = WorldSummary.of(world);
		if (summary.landmarks() == null) {
			feedback.accept(prefix().append(Text.literal("The landmark system is dynamically disabled!").formatted(Formatting.YELLOW)));
			return 0;
		}
		Landmark landmark = summary.landmarks().get(owner, id);
		if (landmark == null) {
			feedback.accept(prefix().append(Text.literal("No landmark exists of that id!").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (!Surveyor.canModify(owner, player)) {
			feedback.accept(prefix().append(Text.literal("You don't have permission to modify that landmark!").formatted(Formatting.YELLOW)));
			return 0;
		}
		summary.landmarks().remove(owner, id);
		feedback.accept(prefix()
			.append(Text.literal(owner.equals(WorldLandmarks.GLOBAL) ? "Landmark " : "Waypoint ").formatted(Formatting.GREEN))
			.append(Text.literal(id.toString()))
			.append(Text.literal(" removed successfully!").formatted(Formatting.GREEN))
		);
		return 1;
	}

	private static int trimLandmark(@Nullable ServerPlayerEntity player, Consumer<Text> feedback, ServerWorld world, UUID owner, Identifier id, Identifier componentType) {
		WorldSummary summary = WorldSummary.of(world);
		if (summary.landmarks() == null) {
			feedback.accept(prefix().append(Text.literal("The landmark system is dynamically disabled!").formatted(Formatting.YELLOW)));
			return 0;
		}
		Landmark landmark = summary.landmarks().get(owner, id);
		if (landmark == null) {
			feedback.accept(prefix().append(Text.literal("No landmark exists of that id!").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (!Surveyor.canModify(owner, player)) {
			feedback.accept(prefix().append(Text.literal("You don't have permission to modify that landmark!").formatted(Formatting.YELLOW)));
			return 0;
		}
		landmark.components().remove(LandmarkComponentType.getType(componentType));
		summary.landmarks().put(landmark);
		feedback.accept(prefix()
			.append(Text.literal(owner.equals(WorldLandmarks.GLOBAL) ? "Landmark " : "Waypoint ").formatted(Formatting.GREEN))
			.append(Text.literal(id.toString()))
			.append(Text.literal(" trimmed successfully!").formatted(Formatting.GREEN))
		);
		return 1;
	}

	private static int appendColor(@Nullable ServerPlayerEntity player, Consumer<Text> feedback, ServerWorld world, UUID owner, Identifier id, String colorString) {
		WorldSummary summary = WorldSummary.of(world);
		if (summary.landmarks() == null) {
			feedback.accept(prefix().append(Text.literal("The landmark system is dynamically disabled!").formatted(Formatting.YELLOW)));
			return 0;
		}
		Landmark landmark = summary.landmarks().get(owner, id);
		if (landmark == null) {
			feedback.accept(prefix().append(Text.literal("No landmark exists of that id!").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (!Surveyor.canModify(landmark.owner(), player)) {
			feedback.accept(prefix().append(Text.literal("You don't have permission to modify that landmark!").formatted(Formatting.YELLOW)));
			return 0;
		}
		DataResult<TextColor> color = TextColor.parse(colorString);
		if (color.isError()) {
			feedback.accept(prefix().append(Text.literal("Not a valid color! Use color names or hex codes").formatted(Formatting.YELLOW)));
			return 0;
		}
		landmark.components().set(LandmarkComponentTypes.COLOR, color.getOrThrow().getRgb());
		summary.landmarks().put(landmark);
		feedback.accept(prefix()
			.append(Text.literal(owner.equals(WorldLandmarks.GLOBAL) ? "Landmark " : "Waypoint ").formatted(Formatting.GREEN))
			.append(Text.literal(id.toString()))
			.append(Text.literal(" appended successfully!").formatted(Formatting.GREEN))
		);
		return 1;
	}

	private static int addBlockLandmark(@Nullable ServerPlayerEntity player, Consumer<Text> feedback, ServerWorld world, UUID owner, BlockPos pos) {
		WorldSummary summary = WorldSummary.of(world);
		if (summary.landmarks() == null) {
			feedback.accept(prefix().append(Text.literal("The landmark system is dynamically disabled!").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (!Surveyor.canModify(owner, player)) {
			feedback.accept(prefix().append(Text.literal("You don't have permission to add that landmark!").formatted(Formatting.YELLOW)));
			return 0;
		}
		Identifier id = Surveyor.id("block/%s/%s/%s".formatted(pos.getX(), pos.getY(), pos.getZ()));
		if (summary.landmarks().contains(owner, id)) {
			feedback.accept(prefix().append(Text.literal("A landmark with this ID already exists! Replacing...").formatted(Formatting.YELLOW)));
		}
		summary.landmarks().put(Landmark.create(owner, id, builder -> LandmarkComponentTypes.forBlock(builder, world, pos)));
		feedback.accept(prefix()
			.append(Text.literal("Added new " + (owner.equals(WorldLandmarks.GLOBAL) ? "Landmark " : "Waypoint ")).formatted(Formatting.GREEN))
			.append(Text.literal(id.toString()))
			.append(Text.literal("!").formatted(Formatting.GREEN))
		);
		return 1;
	}

	private static int addIdLandmark(@Nullable ServerPlayerEntity player, Consumer<Text> feedback, ServerWorld world, UUID owner, Identifier id, BlockPos pos, ItemStackArgument stack, Text name, Text lore) {
		ItemStack icon;
		try {
			icon = stack.createStack(1, false);
		} catch (CommandSyntaxException e) {
			throw new RuntimeException(e);
		}
		WorldSummary summary = WorldSummary.of(world);
		if (summary.landmarks() == null) {
			feedback.accept(prefix().append(Text.literal("The landmark system is dynamically disabled!").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (!Surveyor.canModify(owner, player)) {
			feedback.accept(prefix().append(Text.literal("You don't have permission to add that landmark!").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (summary.landmarks().contains(owner, id)) {
			feedback.accept(prefix().append(Text.literal("A landmark with this ID already exists! Replacing...").formatted(Formatting.YELLOW)));
		}
		summary.landmarks().put(Landmark.create(owner, id, builder -> builder
			.add(LandmarkComponentTypes.POS, pos)
			.add(LandmarkComponentTypes.STACK, icon)
			.add(LandmarkComponentTypes.NAME, name)
			.add(LandmarkComponentTypes.LORE, lore == null ? null : List.of(lore))
		));
		feedback.accept(prefix()
			.append(Text.literal("Added new " + (owner.equals(WorldLandmarks.GLOBAL) ? "Landmark " : "Waypoint ")).formatted(Formatting.GREEN))
			.append(Text.literal(id.toString()))
			.append(Text.literal("!").formatted(Formatting.GREEN))
		);
		return 1;
	}

	private static CompletableFuture<Suggestions> suggestLandmarks(CommandContext<ServerCommandSource> c, SuggestionsBuilder b, boolean global) {
		ServerPlayerEntity player = c.getSource().getPlayer();
		if (player == null) return b.buildFuture();
		boolean op = player.hasPermissionLevel(2);
		SurveyorExploration exploration = op ? null : Surveyor.CONFIG.networking.waypoints.atLeast(NetworkMode.GROUP) ? SurveyorExploration.ofShared(player) : SurveyorExploration.of(player);
		ServerWorld world;
		try {
			world = DimensionArgumentType.getDimensionArgument(c, "dim");
		} catch (CommandSyntaxException e) {
			throw new RuntimeException(e);
		}
		WorldLandmarks landmarks = WorldSummary.of(world).landmarks();
		if (landmarks == null) return b.buildFuture();
		return CommandSource.suggestIdentifiers(global ? landmarks.asMap(WorldLandmarks.GLOBAL, exploration).keySet() : landmarks.asMap(exploration).columnKeySet(), b);
	}

	private static CompletableFuture<Suggestions> suggestOwners(CommandContext<ServerCommandSource> c, SuggestionsBuilder b) {
		ServerPlayerEntity player = c.getSource().getPlayer();
		if (player == null) return b.buildFuture();
		boolean op = player.hasPermissionLevel(2);
		SurveyorExploration exploration = op ? null : Surveyor.CONFIG.networking.waypoints.atLeast(NetworkMode.GROUP) ? SurveyorExploration.ofShared(player) : SurveyorExploration.of(player);
		ServerWorld world;
		Identifier id;
		try {
			world = DimensionArgumentType.getDimensionArgument(c, "dim");
			id = c.getArgument("id", Identifier.class);
		} catch (CommandSyntaxException e) {
			throw new RuntimeException(e);
		}
		WorldLandmarks landmarks = WorldSummary.of(world).landmarks();
		if (landmarks == null) return b.buildFuture();
		return CommandSource.suggestMatching(landmarks.asMap(exploration).columnMap().get(id).keySet().stream().map(UUID::toString), b);
	}

	public static <T> T map(CommandContext<ServerCommandSource> context, SurveyorCommandExecutor<T> executor, boolean feedback) {
		ServerPlayerEntity player = context.getSource().getPlayer();
		SurveyorExploration exploration = player == null ? null : SurveyorExploration.of(player);
		try {
			return executor.execute(context.getSource().getServer(), player, exploration, t -> context.getSource().sendFeedback(() -> t, false));
		} catch (Exception e) {
			if (feedback) context.getSource().sendFeedback(() -> prefix().append(Text.literal("Command failed! Check log for details.").formatted(Formatting.RED)), false);
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
				.executes(c -> execute(c, SurveyorCommands::info))
				.then(Surveyor.CONFIG.networking.globalSharing ?
					CommandManager.literal("share")
						.executes(c -> execute(c, SurveyorCommands::informGlobal)) :
					CommandManager.literal("share")
						.then(CommandManager.argument("player", StringArgumentType.word())
							.suggests((c, b) -> CommandSource.suggestMatching(c.getSource().getServer().getPlayerManager().getPlayerList().stream().filter(p -> c.getSource().getPlayer() != p).map(p -> p.getGameProfile().getName()), b))
							.executes(c -> execute(c, (s, p, e, f) -> share(s, p, f, c.getArgument("player", String.class))))
						)
				).then(Surveyor.CONFIG.networking.globalSharing ?
					CommandManager.literal("unshare")
						.executes(c -> execute(c, SurveyorCommands::informGlobal)) :
					CommandManager.literal("unshare")
						.executes(c -> execute(c, SurveyorCommands::unshare))
				)
		);
		dispatcher.register(
			CommandManager.literal("landmarks")
				.requires(c -> Surveyor.CONFIG.landmarks != SystemMode.DISABLED)
				.executes(c -> execute(c, (s, p, e, f) -> getLandmarks(s, p, f, true)))
				.then(CommandManager.literal("new")
					.requires(c -> c.hasPermissionLevel(2) && Surveyor.CONFIG.landmarks != SystemMode.FROZEN)
					.then(CommandManager.literal("block")
						.then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
							.then(CommandManager.argument("dim", DimensionArgumentType.dimension())
								.executes(c -> execute(c, (s, p, e, f) -> {
									try {
										return addBlockLandmark(p, f, DimensionArgumentType.getDimensionArgument(c, "dim"), WorldLandmarks.GLOBAL, c.getArgument("pos", DefaultPosArgument.class).toAbsoluteBlockPos(c.getSource()));
									} catch (CommandSyntaxException ex) {
										throw new RuntimeException(ex);
									}
								}))
							)
							.executes(c -> execute(c, (s, p, e, f) -> {
								if (p == null) {
									f.accept(prefix().append(Text.literal("missing UUID argument for server console").formatted(Formatting.RED)));
									return 0;
								}
								return addBlockLandmark(p, f, p.getWorld(), WorldLandmarks.GLOBAL, c.getArgument("pos", DefaultPosArgument.class).toAbsoluteBlockPos(c.getSource()));
							}))
						)
					)
					.then(CommandManager.literal("id")
						.then(CommandManager.argument("dim", DimensionArgumentType.dimension())
							.then(CommandManager.argument("id", IdentifierArgumentType.identifier())
								.then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
									.then(CommandManager.argument("icon", ItemStackArgumentType.itemStack(registryAccess))
										.then(CommandManager.argument("name", StringArgumentType.string())
											.then(CommandManager.argument("lore", TextArgumentType.text(registryAccess))
												.executes(c -> execute(c, (s, p, e, f) -> {
													try {
														return addIdLandmark(p, f, DimensionArgumentType.getDimensionArgument(c, "dim"), WorldLandmarks.GLOBAL, c.getArgument("id", Identifier.class), c.getArgument("pos", DefaultPosArgument.class).toAbsoluteBlockPos(c.getSource()), ItemStackArgumentType.getItemStackArgument(c, "icon"), Text.of(c.getArgument("name", String.class)), c.getArgument("lore", Text.class));
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
				)
				.then(CommandManager.literal("append")
					.requires(c -> c.hasPermissionLevel(2))
					.then(CommandManager.argument("id", IdentifierArgumentType.identifier())
						.suggests((c, b) -> suggestLandmarks(c, b, true))
						.then(CommandManager.literal("surveyor:color")
							.then(CommandManager.argument("color", StringArgumentType.greedyString())
								.suggests((c, s) -> CommandSource.suggestMatching(Formatting.getNames(true, false), s))
								.executes(c -> execute(c, (s, p, e, f) -> {
									try {
										return appendColor(p, f, DimensionArgumentType.getDimensionArgument(c, "dim"), WorldLandmarks.GLOBAL, c.getArgument("id", Identifier.class), c.getArgument("color", String.class));
									} catch (CommandSyntaxException ex) {
										throw new RuntimeException(ex);
									}
								}))
							)
						)
					)
				)
				.then(CommandManager.literal("trim")
					.requires(c -> c.hasPermissionLevel(2))
					.then(CommandManager.argument("id", IdentifierArgumentType.identifier())
						.suggests((c, b) -> suggestLandmarks(c, b, true))
						.then(CommandManager.argument("component", IdentifierArgumentType.identifier())
							.suggests((c, b) -> CommandSource.suggestIdentifiers(LandmarkComponentType.keySet(), b))
							.executes(c -> execute(c, (s, p, e, f) -> {
								try {
									return trimLandmark(p, f, DimensionArgumentType.getDimensionArgument(c, "dim"), WorldLandmarks.GLOBAL, c.getArgument("id", Identifier.class), c.getArgument("component", Identifier.class));
								} catch (CommandSyntaxException ex) {
									throw new RuntimeException(ex);
								}
							}))
						)
					)
				)
				.then(CommandManager.literal("view")
					.then(CommandManager.argument("dim", DimensionArgumentType.dimension())
						.then(CommandManager.argument("id", IdentifierArgumentType.identifier())
							.suggests((c, b) -> suggestLandmarks(c, b, true))
							.executes(c -> execute(c, (s, p, e, f) -> {
								try {
									return viewLandmark(p, f, DimensionArgumentType.getDimensionArgument(c, "dim"), WorldLandmarks.GLOBAL, c.getArgument("id", Identifier.class), false);
								} catch (CommandSyntaxException ex) {
									throw new RuntimeException(ex);
								}
							}))
						)
					)
				)
				.then(CommandManager.literal("raw")
					.requires(c -> Surveyor.CONFIG.debugCommands)
					.then(CommandManager.argument("dim", DimensionArgumentType.dimension())
						.then(CommandManager.argument("id", IdentifierArgumentType.identifier())
							.suggests((c, b) -> suggestLandmarks(c, b, true))
							.executes(c -> execute(c, (s, p, e, f) -> {
								try {
									return viewLandmark(p, f, DimensionArgumentType.getDimensionArgument(c, "dim"), WorldLandmarks.GLOBAL, c.getArgument("id", Identifier.class), true);
								} catch (CommandSyntaxException ex) {
									throw new RuntimeException(ex);
								}
							}))
						)
					)
				)
				.then(CommandManager.literal("remove")
					.requires(c -> c.hasPermissionLevel(2) && Surveyor.CONFIG.landmarks != SystemMode.FROZEN)
					.then(CommandManager.argument("dim", DimensionArgumentType.dimension())
						.then(CommandManager.argument("id", IdentifierArgumentType.identifier())
							.suggests((c, b) -> suggestLandmarks(c, b, true))
							.executes(c -> execute(c, (s, p, e, f) -> {
								try {
									return removeLandmark(p, f, DimensionArgumentType.getDimensionArgument(c, "dim"), WorldLandmarks.GLOBAL, c.getArgument("id", Identifier.class));
								} catch (CommandSyntaxException ex) {
									throw new RuntimeException(ex);
								}
							}))
						)
					)
				)
		);
		dispatcher.register(
			CommandManager.literal("waypoints")
				.requires(c -> Surveyor.CONFIG.landmarks != SystemMode.DISABLED)
				.executes(c -> execute(c, (s, p, e, f) -> getLandmarks(s, p, f, false)))
				.then(CommandManager.literal("new")
					.requires(c -> Surveyor.CONFIG.landmarks != SystemMode.FROZEN)
					.then(CommandManager.literal("block")
						.then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
							.then(CommandManager.argument("dim", DimensionArgumentType.dimension())
								.then(CommandManager.argument("owner", UuidArgumentType.uuid())
									.suggests(SurveyorCommands::suggestOwners)
									.requires(c -> c.hasPermissionLevel(2))
									.executes(c -> execute(c, (s, p, e, f) -> {
										try {
											return addBlockLandmark(p, f, DimensionArgumentType.getDimensionArgument(c, "dim"), UuidArgumentType.getUuid(c, "owner"), c.getArgument("pos", DefaultPosArgument.class).toAbsoluteBlockPos(c.getSource()));
										} catch (CommandSyntaxException ex) {
											throw new RuntimeException(ex);
										}
									}))
								)
								.executes(c -> execute(c, (s, p, e, f) -> {
									try {
										if (p == null) {
											f.accept(prefix().append(Text.literal("missing UUID argument for server console").formatted(Formatting.RED)));
											return 0;
										}
										return addBlockLandmark(p, f, DimensionArgumentType.getDimensionArgument(c, "dim"), Surveyor.getUuid(p), c.getArgument("pos", DefaultPosArgument.class).toAbsoluteBlockPos(c.getSource()));
									} catch (CommandSyntaxException ex) {
										throw new RuntimeException(ex);
									}
								}))
							)
							.executes(c -> execute(c, (s, p, e, f) -> {
								if (p == null) {
									f.accept(prefix().append(Text.literal("missing UUID argument for server console").formatted(Formatting.RED)));
									return 0;
								}
								return addBlockLandmark(p, f, p.getWorld(), Surveyor.getUuid(p), c.getArgument("pos", DefaultPosArgument.class).toAbsoluteBlockPos(c.getSource()));
							}))
						)
					)
					.then(CommandManager.literal("id")
						.then(CommandManager.argument("uuid", UuidArgumentType.uuid())
							.then(CommandManager.argument("dim", DimensionArgumentType.dimension())
								.then(CommandManager.argument("id", IdentifierArgumentType.identifier())
									.then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
										.then(CommandManager.argument("icon", ItemStackArgumentType.itemStack(registryAccess))
											.then(CommandManager.argument("name", StringArgumentType.string())
												.then(CommandManager.argument("lore", TextArgumentType.text(registryAccess))
													.executes(c -> execute(c, (s, p, e, f) -> {
														try {
															return addIdLandmark(p, f, DimensionArgumentType.getDimensionArgument(c, "dim"), UuidArgumentType.getUuid(c, "uuid"), c.getArgument("id", Identifier.class), c.getArgument("pos", DefaultPosArgument.class).toAbsoluteBlockPos(c.getSource()), ItemStackArgumentType.getItemStackArgument(c, "icon"), Text.of(c.getArgument("name", String.class)), c.getArgument("lore", Text.class));
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
						.then(CommandManager.argument("dim", DimensionArgumentType.dimension())
							.then(CommandManager.argument("id", IdentifierArgumentType.identifier())
								.then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
									.then(CommandManager.argument("icon", ItemStackArgumentType.itemStack(registryAccess))
										.then(CommandManager.argument("name", StringArgumentType.string())
											.then(CommandManager.argument("lore", TextArgumentType.text(registryAccess))
												.executes(c -> execute(c, (s, p, e, f) -> {
													try {
														if (p == null) {
															f.accept(prefix().append(Text.literal("missing UUID argument for server console").formatted(Formatting.RED)));
															return 0;
														}
														return addIdLandmark(p, f, DimensionArgumentType.getDimensionArgument(c, "dim"), Surveyor.getUuid(p), c.getArgument("id", Identifier.class), c.getArgument("pos", DefaultPosArgument.class).toAbsoluteBlockPos(c.getSource()), ItemStackArgumentType.getItemStackArgument(c, "icon"), Text.of(c.getArgument("name", String.class)), c.getArgument("lore", Text.class));
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
				)
				.then(CommandManager.literal("append")
					.requires(c -> c.hasPermissionLevel(2))
					.then(CommandManager.argument("id", IdentifierArgumentType.identifier())
						.suggests((c, b) -> suggestLandmarks(c, b, true))
						.then(CommandManager.literal("surveyor:color")
							.then(CommandManager.argument("color", StringArgumentType.greedyString())
								.suggests((c, s) -> CommandSource.suggestMatching(Formatting.getNames(true, false), s))
								.then(CommandManager.argument("dim", DimensionArgumentType.dimension())
									.then(CommandManager.argument("owner", UuidArgumentType.uuid())
									.suggests(SurveyorCommands::suggestOwners)
										.requires(c -> c.hasPermissionLevel(2))
										.executes(c -> execute(c, (s, p, e, f) -> {
											try {
												return appendColor(p, f, DimensionArgumentType.getDimensionArgument(c, "dim"), UuidArgumentType.getUuid(c, "owner"), c.getArgument("id", Identifier.class), c.getArgument("color", String.class));
											} catch (CommandSyntaxException ex) {
												throw new RuntimeException(ex);
											}
										}))
									)
									.executes(c -> execute(c, (s, p, e, f) -> {
										try {
											if (p == null) {
												f.accept(prefix().append(Text.literal("missing UUID argument for server console").formatted(Formatting.RED)));
												return 0;
											}
											return appendColor(p, f, DimensionArgumentType.getDimensionArgument(c, "dim"), Surveyor.getUuid(p), c.getArgument("id", Identifier.class), c.getArgument("color", String.class));
										} catch (CommandSyntaxException ex) {
											throw new RuntimeException(ex);
										}
									}))
								)
								.executes(c -> execute(c, (s, p, e, f) -> {
									if (p == null) {
										f.accept(prefix().append(Text.literal("missing UUID argument for server console").formatted(Formatting.RED)));
										return 0;
									}
									return appendColor(p, f, p.getWorld(), Surveyor.getUuid(p), c.getArgument("id", Identifier.class), c.getArgument("color", String.class));
								}))
							)
						)
					)
				)
				.then(CommandManager.literal("trim")
					.requires(c -> c.hasPermissionLevel(2))
					.then(CommandManager.argument("id", IdentifierArgumentType.identifier())
						.suggests((c, b) -> suggestLandmarks(c, b, true))
						.then(CommandManager.argument("component", IdentifierArgumentType.identifier())
							.suggests((c, b) -> CommandSource.suggestIdentifiers(LandmarkComponentType.keySet(), b))
							.then(CommandManager.argument("dim", DimensionArgumentType.dimension())
								.then(CommandManager.argument("owner", UuidArgumentType.uuid())
									.suggests(SurveyorCommands::suggestOwners)
									.requires(c -> c.hasPermissionLevel(2))
									.executes(c -> execute(c, (s, p, e, f) -> {
										try {
											return trimLandmark(p, f, DimensionArgumentType.getDimensionArgument(c, "dim"), UuidArgumentType.getUuid(c, "owner"), c.getArgument("id", Identifier.class), c.getArgument("component", Identifier.class));
										} catch (CommandSyntaxException ex) {
											throw new RuntimeException(ex);
										}
									}))
								)
								.executes(c -> execute(c, (s, p, e, f) -> {
									try {
										if (p == null) {
											f.accept(prefix().append(Text.literal("missing UUID argument for server console").formatted(Formatting.RED)));
											return 0;
										}
										return trimLandmark(p, f, DimensionArgumentType.getDimensionArgument(c, "dim"), Surveyor.getUuid(p), c.getArgument("id", Identifier.class), c.getArgument("component", Identifier.class));
									} catch (CommandSyntaxException ex) {
										throw new RuntimeException(ex);
									}
								}))
							)
							.executes(c -> execute(c, (s, p, e, f) -> {
								if (p == null) {
									f.accept(prefix().append(Text.literal("missing UUID argument for server console").formatted(Formatting.RED)));
									return 0;
								}
								return trimLandmark(p, f, p.getWorld(), Surveyor.getUuid(p), c.getArgument("id", Identifier.class), c.getArgument("component", Identifier.class));
							}))
						)
					)
				)
				.then(CommandManager.literal("view")
					.then(CommandManager.argument("dim", DimensionArgumentType.dimension())
						.then(CommandManager.argument("id", IdentifierArgumentType.identifier())
							.suggests((c, b) -> suggestLandmarks(c, b, false))
							.then(CommandManager.argument("owner", UuidArgumentType.uuid())
									.suggests(SurveyorCommands::suggestOwners)
								.requires(c -> c.hasPermissionLevel(2))
								.executes(c -> execute(c, (s, p, e, f) -> {
									try {
										return viewLandmark(p, f, DimensionArgumentType.getDimensionArgument(c, "dim"), UuidArgumentType.getUuid(c, "owner"), c.getArgument("id", Identifier.class), false);
									} catch (CommandSyntaxException ex) {
										throw new RuntimeException(ex);
									}
								}))
							)
							.executes(c -> execute(c, (s, p, e, f) -> {
								try {
									if (p == null) {
										f.accept(prefix().append(Text.literal("missing UUID argument for server console").formatted(Formatting.RED)));
										return 0;
									}
									return viewLandmark(p, f, DimensionArgumentType.getDimensionArgument(c, "dim"), Surveyor.getUuid(p), c.getArgument("id", Identifier.class), false);
								} catch (CommandSyntaxException ex) {
									throw new RuntimeException(ex);
								}
							}))
						)
					)
				)
				.then(CommandManager.literal("raw")
					.requires(c -> Surveyor.CONFIG.debugCommands)
					.then(CommandManager.argument("dim", DimensionArgumentType.dimension())
						.then(CommandManager.argument("id", IdentifierArgumentType.identifier())
							.suggests((c, b) -> suggestLandmarks(c, b, true))
							.then(CommandManager.argument("owner", UuidArgumentType.uuid())
									.suggests(SurveyorCommands::suggestOwners)
								.requires(c -> c.hasPermissionLevel(2))
								.executes(c -> execute(c, (s, p, e, f) -> {
									try {
										return viewLandmark(p, f, DimensionArgumentType.getDimensionArgument(c, "dim"), UuidArgumentType.getUuid(c, "owner"), c.getArgument("id", Identifier.class), true);
									} catch (CommandSyntaxException ex) {
										throw new RuntimeException(ex);
									}
								}))
							)
							.executes(c -> execute(c, (s, p, e, f) -> {
								try {
									if (p == null) {
										f.accept(prefix().append(Text.literal("missing UUID argument for server console").formatted(Formatting.RED)));
										return 0;
									}
									return viewLandmark(p, f, DimensionArgumentType.getDimensionArgument(c, "dim"), Surveyor.getUuid(p), c.getArgument("id", Identifier.class), true);
								} catch (CommandSyntaxException ex) {
									throw new RuntimeException(ex);
								}
							}))
						)
					)
				)
				.then(CommandManager.literal("remove")
					.requires(c -> Surveyor.CONFIG.landmarks != SystemMode.FROZEN)
					.then(CommandManager.argument("dim", DimensionArgumentType.dimension())
						.then(CommandManager.argument("id", IdentifierArgumentType.identifier())
							.suggests((c, b) -> suggestLandmarks(c, b, false))
							.then(CommandManager.argument("owner", UuidArgumentType.uuid())
									.suggests(SurveyorCommands::suggestOwners)
								.requires(c -> c.hasPermissionLevel(2))
								.executes(c -> execute(c, (s, p, e, f) -> {
									try {
										return removeLandmark(p, f, DimensionArgumentType.getDimensionArgument(c, "dim"), UuidArgumentType.getUuid(c, "owner"), c.getArgument("id", Identifier.class));
									} catch (CommandSyntaxException ex) {
										throw new RuntimeException(ex);
									}
								}))
							)
							.executes(c -> execute(c, (s, p, e, f) -> {
								try {
									if (p == null) {
										f.accept(prefix().append(Text.literal("missing UUID argument for server console").formatted(Formatting.RED)));
										return 0;
									}
									return removeLandmark(p, f, DimensionArgumentType.getDimensionArgument(c, "dim"), Surveyor.getUuid(p), c.getArgument("id", Identifier.class));
								} catch (CommandSyntaxException ex) {
									throw new RuntimeException(ex);
								}
							}))
						)
					)
				)
		);
	}

	public interface SurveyorCommandExecutor<T> {
		T execute(MinecraftServer server, @Nullable ServerPlayerEntity player, @Nullable SurveyorExploration exploration, Consumer<Text> feedback);
	}
}
