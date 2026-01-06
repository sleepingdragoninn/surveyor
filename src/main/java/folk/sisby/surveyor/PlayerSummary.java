package folk.sisby.surveyor;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import folk.sisby.surveyor.config.NetworkMode;
import folk.sisby.surveyor.packet.S2CStructuresAddedPacket;
import folk.sisby.surveyor.packet.S2CUpdateRegionPacket;
import folk.sisby.surveyor.structure.WorldStructures;
import folk.sisby.surveyor.terrain.WorldTerrain;
import folk.sisby.surveyor.util.ArrayUtil;
import folk.sisby.surveyor.util.RegionPos;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public interface PlayerSummary {
	String KEY_DATA = "surveyor";
	String KEY_USERNAME = "username";

	static PlayerSummary of(ServerPlayerEntity player) {
		return ((SurveyorPlayer) player).surveyor$getSummary();
	}

	static PlayerSummary of(UUID uuid, MinecraftServer server) {
		return ServerSummary.of(server).getPlayer(uuid);
	}

	SurveyorExploration exploration();

	String username();

	RegistryKey<World> dimension();

	Vec3d pos();

	float yaw();

	int viewDistance();

	boolean online();


	default void copyFrom(PlayerSummary oldSummary) {
		exploration().copyFrom(oldSummary.exploration());
	}

	record OfflinePlayerSummary(SurveyorExploration exploration, String username, RegistryKey<World> dimension, Vec3d pos, float yaw, boolean online) implements PlayerSummary {
		public OfflinePlayerSummary(UUID uuid, NbtCompound nbt, boolean online) {
			this(
				OfflinePlayerExploration.from(uuid, nbt.getCompound(KEY_DATA)),
				nbt.getCompound(KEY_DATA).contains(KEY_USERNAME) ? nbt.getCompound(KEY_DATA).getString(KEY_USERNAME) : "???",
				RegistryKey.of(RegistryKeys.WORLD, new Identifier(nbt.getString("Dimension"))),
				ArrayUtil.toVec3d(nbt.getList("Pos", NbtElement.DOUBLE_TYPE).stream().mapToDouble(e -> ((NbtDouble) e).doubleValue()).toArray()),
				nbt.getList("Rotation", NbtElement.FLOAT_TYPE).getFloat(0),
				online
			);
		}

		public OfflinePlayerSummary(ServerPlayerEntity player) {
			this(
				PlayerSummary.OfflinePlayerSummary.OfflinePlayerExploration.ofMerged(Set.of(SurveyorExploration.of(player))),
				player.getGameProfile().getName(),
				player.getWorld().getRegistryKey(),
				player.getPos(),
				player.getYaw(),
				true
			);
		}

		public static void writeBuf(PacketByteBuf buf, PlayerSummary summary) {
			buf.writeString(summary.username());
			buf.writeRegistryKey(summary.dimension());
			buf.writeDouble(summary.pos().x);
			buf.writeDouble(summary.pos().y);
			buf.writeDouble(summary.pos().z);
			buf.writeFloat(summary.yaw());
			buf.writeBoolean(summary.online());
		}

		public static PlayerSummary readBuf(PacketByteBuf buf) {
			return new OfflinePlayerSummary(
				null,
				buf.readString(),
				buf.readRegistryKey(RegistryKeys.WORLD),
				new Vec3d(
					buf.readDouble(),
					buf.readDouble(),
					buf.readDouble()
				),
				buf.readFloat(),
				buf.readBoolean()
			);
		}

		@Override
		public int viewDistance() {
			return 0;
		}

		public record OfflinePlayerExploration(Set<UUID> sharedPlayers, Table<RegistryKey<World>, RegionPos, BitSet> chunks, Table<RegistryKey<World>, RegistryKey<Structure>, LongSet> starts, boolean personal) implements SurveyorExploration {
			public static OfflinePlayerExploration empty(UUID uuid) {
				return new OfflinePlayerExploration(Set.of(uuid), HashBasedTable.create(), HashBasedTable.create(), true);
			}

			public static OfflinePlayerExploration ofMerged(Set<SurveyorExploration> explorations) {
				Set<UUID> sharedPlayers = new HashSet<>();
				Table<RegistryKey<World>, RegionPos, BitSet> chunks = HashBasedTable.create();
				Table<RegistryKey<World>, RegistryKey<Structure>, LongSet> starts = HashBasedTable.create();
				OfflinePlayerExploration outExploration = new OfflinePlayerExploration(sharedPlayers, chunks, starts, false);
				for (SurveyorExploration exploration : explorations) {
					sharedPlayers.addAll(exploration.sharedPlayers());
					exploration.chunks().cellSet().forEach(c -> outExploration.mergeRegion(c.getRowKey(), c.getColumnKey(), c.getValue(), false));
					exploration.starts().cellSet().forEach(c -> outExploration.mergeStructures(c.getRowKey(), c.getColumnKey(), c.getValue()));
				}
				return outExploration;
			}

			public static SurveyorExploration from(UUID uuid, NbtCompound nbt) {
				OfflinePlayerExploration mutable = new OfflinePlayerExploration(new HashSet<>(Set.of(uuid)), HashBasedTable.create(), HashBasedTable.create(), true);
				mutable.read(nbt);
				return mutable;
			}
		}
	}

	class PlayerEntitySummary implements PlayerSummary {
		private final PlayerEntity player;

		public PlayerEntitySummary(PlayerEntity player) {
			this.player = player;
		}

		@Override
		public SurveyorExploration exploration() {
			return null;
		}

		@Override
		public String username() {
			return player.getGameProfile().getName();
		}

		@Override
		public RegistryKey<World> dimension() {
			return player.getWorld().getRegistryKey();
		}

		@Override
		public Vec3d pos() {
			return player.getPos();
		}

		@Override
		public float yaw() {
			return player.getYaw();
		}

		@Override
		public int viewDistance() {
			return 0;
		}

		@Override
		public boolean online() {
			return true;
		}
	}

	class ServerPlayerEntitySummary extends PlayerEntitySummary implements PlayerSummary {
		private final ServerPlayerExploration exploration;
		private int viewDistance;

		public ServerPlayerEntitySummary(ServerPlayerEntity player) {
			super(player);
			this.exploration = new ServerPlayerExploration(player, Tables.synchronizedTable(HashBasedTable.create()), Tables.synchronizedTable(HashBasedTable.create()));
		}

		@Override
		public SurveyorExploration exploration() {
			return exploration;
		}

		@Override
		public int viewDistance() {
			return viewDistance;
		}

		@Override
		public void copyFrom(PlayerSummary oldSummary) {
			super.copyFrom(oldSummary);
			viewDistance = oldSummary.viewDistance();
		}

		public void setViewDistance(int viewDistance) {
			this.viewDistance = viewDistance;
		}

		public void read(NbtCompound nbt) {
			exploration.read(nbt.getCompound(KEY_DATA));
		}

		public void writeNbt(NbtCompound nbt) {
			NbtCompound surveyorNbt = new NbtCompound();
			exploration.write(surveyorNbt);
			surveyorNbt.putString(PlayerSummary.KEY_USERNAME, username());
			nbt.put(PlayerSummary.KEY_DATA, surveyorNbt);
		}

		public record ServerPlayerExploration(ServerPlayerEntity player, Table<RegistryKey<World>, RegionPos, BitSet> chunks, Table<RegistryKey<World>, RegistryKey<Structure>, LongSet> starts) implements SurveyorExploration {
			@Override
			public Set<UUID> sharedPlayers() {
				return Set.of(Surveyor.getUuid(player));
			}

			@Override
			public boolean personal() {
				return true;
			}

			@Override
			public void mergeRegion(RegistryKey<World> dimension, RegionPos regionPos, BitSet chunks, boolean updateClient) { // This method is currently unused for server players, but its implemented anyway
				WorldSummary summary = WorldSummary.of(player.getServer().getWorld(dimension));
				WorldTerrain terrain = summary == null ? null : summary.terrain();
				if (player.getServer().isHost(player.getGameProfile())) {
					updateClientForMergeRegion(summary, regionPos, chunks);
				}
				if (Surveyor.CONFIG.networking.terrain.atLeast(NetworkMode.SOLO)) {
					for (ServerPlayerEntity friend : ServerSummary.of(player.getServer()).getSharingPlayers(Surveyor.getUuid(player), Surveyor.CONFIG.networking.terrain, !updateClient)) {
						SurveyorExploration friendExploration = SurveyorExploration.of(friend);
						BitSet sendSet = (BitSet) chunks.clone();
						if (friendExploration.chunks().contains(dimension, regionPos)) sendSet.andNot(friendExploration.chunks().get(dimension, regionPos));
						if (!sendSet.isEmpty() && terrain != null) S2CUpdateRegionPacket.of(dimension, friend != player, regionPos, terrain.getRegion(regionPos), sendSet).send(friend);
					}
				}
				SurveyorExploration.super.mergeRegion(dimension, regionPos, chunks, updateClient);
			}

			@Override
			public void addChunk(RegistryKey<World> dimension, ChunkPos pos, boolean updateClient) {
				WorldSummary summary = WorldSummary.of(player.getServer().getWorld(dimension));
				if (Surveyor.CONFIG.networking.terrain.atLeast(NetworkMode.SOLO)) {
					RegionPos regionPos = RegionPos.of(pos);
					WorldTerrain terrain = summary == null ? null : summary.terrain();
					if (terrain == null) return;
					S2CUpdateRegionPacket packet = S2CUpdateRegionPacket.of(dimension, true, regionPos, terrain.getRegion(regionPos), RegionPos.chunkToBitSet(pos));
					packet.send(Surveyor.getUuid(player), player.getServer(), p -> !SurveyorExploration.of(p).exploredChunk(dimension, pos), Surveyor.CONFIG.networking.terrain, updateClient);
				}
				SurveyorExploration.super.addChunk(dimension, pos, updateClient);
				if (player.getServer().isHost(player.getGameProfile())) updateClientForAddChunk(summary, pos);
			}

			@Override
			public void addStructure(RegistryKey<World> dimension, RegistryKey<Structure> structureKey, ChunkPos pos) {
				WorldSummary summary = WorldSummary.of(player.getServer().getWorld(dimension));
				WorldStructures structures = summary == null ? null : summary.structures();
				if (structures != null && Surveyor.CONFIG.networking.structures.atLeast(NetworkMode.SOLO)) {
					S2CStructuresAddedPacket packet = S2CStructuresAddedPacket.of(false, structureKey, pos, structures);
					packet.send(Surveyor.getUuid(player), player.getServer(), p -> !SurveyorExploration.of(p).exploredStructure(dimension, structureKey, pos), Surveyor.CONFIG.networking.structures, false);
				}
				SurveyorExploration.super.addStructure(dimension, structureKey, pos);
				if (player.getServer().isHost(player.getGameProfile())) updateClientForAddStructure(summary, structureKey, pos);
			}
		}
	}
}
