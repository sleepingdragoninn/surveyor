package folk.sisby.surveyor.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.SurveyorExploration;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.config.SystemMode;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
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
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static folk.sisby.surveyor.SurveyorCommands.indent;
import static folk.sisby.surveyor.SurveyorCommands.prefix;

public class SurveyorClientCommands {

	private static int getLandmarks(WorldSummary summary, SurveyorExploration exploration, Consumer<Text> feedback, boolean global) {
		if (summary.landmarks() == null) {
			feedback.accept(prefix().append(Text.literal("The landmark system is dynamically disabled!").formatted(Formatting.YELLOW)));
			return 0;
		}
		Map<Identifier, Landmark> landmarks = summary.landmarks().asMap(global ? WorldLandmarks.GLOBAL : SurveyorClient.getClientUuid(), SurveyorClient.getExploration());
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
							.append(landmark.components().get(LandmarkComponentTypes.NAME).copy().styled(s -> s.withColor(landmark.components().contains(LandmarkComponentTypes.COLOR) ? 0xFFFFFF & landmark.components().get(LandmarkComponentTypes.COLOR) : Formatting.GREEN.getColorValue())))
							.append(Text.literal("\""))
					)
			);
		}
		return landmarks.size();
	}


	private static int viewLandmark(WorldSummary summary, Consumer<Text> feedback, Identifier id, boolean global) {
		if (summary.landmarks() == null) {
			feedback.accept(prefix().append(Text.literal("The landmark system is dynamically disabled!").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (!summary.landmarks().contains(global ? WorldLandmarks.GLOBAL : SurveyorClient.getClientUuid(), id)) {
			feedback.accept(prefix().append(Text.literal("No landmark exists of that id!").formatted(Formatting.YELLOW)));
			return 0;
		}
		Landmark landmark = summary.landmarks().get(global ? WorldLandmarks.GLOBAL : SurveyorClient.getClientUuid(), id);
		feedback.accept(prefix().append(Text.literal(landmark.owner().equals(WorldLandmarks.GLOBAL) ? "Landmark " : "Waypoint ").formatted(Formatting.GRAY)).append(Text.literal(id.toString())).append(Text.literal(": ")));
		landmark.toText().forEach(t -> feedback.accept(indent().append(t)));
		return 1;
	}

	private static int rawLandmark(WorldSummary summary, Consumer<Text> feedback, Identifier id, boolean global) {
		if (summary.landmarks() == null) {
			feedback.accept(prefix().append(Text.literal("The landmark system is dynamically disabled!").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (!summary.landmarks().contains(global ? WorldLandmarks.GLOBAL : SurveyorClient.getClientUuid(), id)) {
			feedback.accept(prefix().append(Text.literal("No landmark exists of that id!").formatted(Formatting.YELLOW)));
			return 0;
		}
		Landmark landmark = summary.landmarks().get(global ? WorldLandmarks.GLOBAL : SurveyorClient.getClientUuid(), id);
		feedback.accept(prefix().append(Text.literal(landmark.owner().equals(WorldLandmarks.GLOBAL) ? "Landmark " : "Waypoint ").formatted(Formatting.GRAY)).append(Text.literal(id.toString())).append(Text.literal(": ")).append(Text.literal(landmark.toNbt().toString()).formatted(Formatting.AQUA)));
		return 1;
	}

	private static int removeLandmark(WorldSummary summary, Consumer<Text> feedback, Identifier id, boolean global) {
		if (summary.landmarks() == null) {
			feedback.accept(prefix().append(Text.literal("The landmark system is dynamically disabled!").formatted(Formatting.YELLOW)));
			return 0;
		}
		if (!summary.landmarks().contains(global ? WorldLandmarks.GLOBAL : SurveyorClient.getClientUuid(), id)) {
			feedback.accept(prefix().append(Text.literal("No landmark exists of that id!").formatted(Formatting.YELLOW)));
			return 0;
		}
		Landmark landmark = summary.landmarks().get(global ? WorldLandmarks.GLOBAL : SurveyorClient.getClientUuid(), id);
		summary.landmarks().remove(global ? WorldLandmarks.GLOBAL : SurveyorClient.getClientUuid(), id);
		feedback.accept(prefix().append(Text.literal("%s %s removed successfully!".formatted(landmark.owner().equals(WorldLandmarks.GLOBAL) ? "Landmark" : "Waypoint", id)).formatted(Formatting.GREEN)));
		return 1;
	}

	private static int addBlockLandmark(WorldSummary summary, World world, Consumer<Text> feedback, BlockPos pos, boolean global) {
		if (summary.landmarks() == null) {
			feedback.accept(prefix().append(Text.literal("The landmark system is dynamically disabled!").formatted(Formatting.YELLOW)));
			return 0;
		}
		Identifier id = Surveyor.id("block/%s/%s/%s".formatted(pos.getX(), pos.getY(), pos.getZ()));
		if (summary.landmarks().contains(global ? WorldLandmarks.GLOBAL : SurveyorClient.getClientUuid(), id)) {
			feedback.accept(prefix().append(Text.literal("A landmark with this ID already exists! Replacing...").formatted(Formatting.YELLOW)));
		}
		summary.landmarks().put(Landmark.create(global ? WorldLandmarks.GLOBAL : SurveyorClient.getClientUuid(), id, builder -> LandmarkComponentTypes.forBlock(builder, world, pos)));
		feedback.accept(prefix().append(Text.literal("Added new %s %s!".formatted(global ? "Landmark" : "Waypoint", id)).formatted(Formatting.GREEN)));
		return 1;
	}

	public static <T> T map(CommandContext<FabricClientCommandSource> context, SurveyorCommandExecutor<T> executor, boolean feedback) {
		ClientPlayerEntity player = context.getSource().getPlayer();
		SurveyorExploration exploration = SurveyorClient.getExploration();
		try {
			return executor.execute(WorldSummary.of(context.getSource().getWorld()), player, context.getSource().getWorld(), exploration, t -> context.getSource().sendFeedback(t));
		} catch (Exception e) {
			if (feedback) context.getSource().sendFeedback(Text.literal("Command failed! Check log for details.").formatted(Formatting.RED));
			if (feedback) Surveyor.LOGGER.error("[Surveyor] Error while executing command: {}", context.getInput(), e);
			return null;
		}
	}

	public static int execute(CommandContext<FabricClientCommandSource> context, SurveyorCommandExecutor<Integer> executor) {
		return Objects.requireNonNullElse(map(context, executor, true), 0);
	}

	private static ServerCommandSource sourceForPos(FabricClientCommandSource source) {
		return new ServerCommandSource(null, source.getPosition(), source.getRotation(), null, 0, null, null, null, source.getEntity());
	}

	public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
		dispatcher.register(
			ClientCommandManager.literal("waypointsc")
				.requires(c -> !c.getClient().isInSingleplayer() && c.getClient().getNetworkHandler().getCommandDispatcher().findNode(List.of("surveyor")) == null)
				.requires(c -> Surveyor.CONFIG.landmarks != SystemMode.DISABLED)
				.executes(c -> execute(c, (w, p, sw, e, f) -> getLandmarks(w, e, f, false)))
				.then(ClientCommandManager.literal("new")
					.requires(c -> Surveyor.CONFIG.landmarks != SystemMode.FROZEN)
					.then(ClientCommandManager.literal("block")
						.then(ClientCommandManager.argument("pos", BlockPosArgumentType.blockPos())
							.executes(c -> execute(c, (w, p, sw, e, f) -> addBlockLandmark(w, sw, f, c.getArgument("pos", DefaultPosArgument.class).toAbsoluteBlockPos(sourceForPos(c.getSource())), false)))
						)
					)
				)
				.then(ClientCommandManager.literal("view")
					.then(ClientCommandManager.argument("id", IdentifierArgumentType.identifier())
						.suggests((c, b) -> CommandSource.suggestIdentifiers((Iterable<Identifier>) map(c, (w, p, sw, e, f) -> w.landmarks() == null ? new HashSet<Identifier>() : w.landmarks().asMap(SurveyorClient.getClientUuid(), p.hasPermissionLevel(2) ? null : e).keySet(), false), b))
						.executes(c -> execute(c, (w, p, sw, e, f) -> viewLandmark(w, f, c.getArgument("id", Identifier.class), false)))
					)
				)
				.then(ClientCommandManager.literal("raw")
					.requires(c -> Surveyor.CONFIG.debugCommands)
					.then(ClientCommandManager.argument("id", IdentifierArgumentType.identifier())
						.suggests((c, b) -> CommandSource.suggestIdentifiers((Iterable<Identifier>) map(c, (w, p, sw, e, f) -> w.landmarks() == null ? new HashSet<Identifier>() : w.landmarks().asMap(SurveyorClient.getClientUuid(), p.hasPermissionLevel(2) ? null : e).keySet(), false), b))
						.executes(c -> execute(c, (w, p, sw, e, f) -> rawLandmark(w, f, c.getArgument("id", Identifier.class), false)))
					)
				)
				.then(ClientCommandManager.literal("remove")
					.requires(c -> Surveyor.CONFIG.landmarks != SystemMode.FROZEN)
					.then(ClientCommandManager.argument("id", IdentifierArgumentType.identifier())
						.suggests((c, b) -> CommandSource.suggestIdentifiers((Iterable<Identifier>) map(c, (w, p, sw, e, f) -> w.landmarks() == null ? new HashSet<Identifier>() : w.landmarks().asMap(SurveyorClient.getClientUuid(), p.hasPermissionLevel(2) ? null : e).keySet(), false), b))
						.executes(c -> execute(c, (w, p, sw, e, f) -> removeLandmark(w, f, c.getArgument("id", Identifier.class), false)))
					)
				)
		);
	}

	public interface SurveyorCommandExecutor<T> {
		T execute(WorldSummary currentWorldSummary, ClientPlayerEntity player, ClientWorld world, SurveyorExploration exploration, Consumer<Text> feedback);
	}
}
