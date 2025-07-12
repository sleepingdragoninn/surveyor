package folk.sisby.surveyor;

import folk.sisby.surveyor.config.NetworkMode;
import folk.sisby.surveyor.packet.S2CStructuresAddedPacket;
import folk.sisby.surveyor.packet.S2CUpdateRegionPacket;
import folk.sisby.surveyor.structure.WorldStructureSummary;
import folk.sisby.surveyor.terrain.RegionSummary;
import folk.sisby.surveyor.terrain.WorldTerrainSummary;
import folk.sisby.surveyor.util.ArrayUtil;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public interface PlayerSummary {
	String KEY_DATA = "surveyor";
	String KEY_USERNAME = "username";

	static PlayerSummary of(ServerPlayerEntity player) {
		return ((SurveyorPlayer) player).surveyor$getSummary();
	}

	static PlayerSummary of(UUID uuid, MinecraftServer server) {
		return ServerSummary.of(server).getPlayer(uuid, server);
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
				OfflinePlayerExploration.from(uuid, nbt.getCompound(KEY_DATA).orElse(new NbtCompound())),
				nbt.getCompound(KEY_DATA).isPresent() && nbt.getCompound(KEY_DATA).get().getString(KEY_USERNAME).isPresent()
					? nbt.getCompound(KEY_DATA).get().getString(KEY_USERNAME).get()
					: "???",
				RegistryKey.of(RegistryKeys.WORLD, Identifier.of(nbt.getString("Dimension").orElse(World.OVERWORLD.getValue().toString()))),
				nbt.getList("Pos").isPresent()
					? ArrayUtil.toVec3d(nbt.getList("Pos").get().stream().mapToDouble(e -> ((NbtDouble) e).doubleValue()).toArray())
					: new Vec3d(0, 0, 0),
				nbt.getList("Rotation").isPresent()
					? nbt.getList("Rotation").get().getFloat(0).orElse(0F)
					: 0,
				online
			);
		}

		public static void writeBuf(PlayerSummary summary, RegistryByteBuf buf) {
			buf.writeString(summary.username());
			buf.writeRegistryKey(summary.dimension());
			buf.writeVec3d(summary.pos());
			buf.writeFloat(summary.yaw());
			buf.writeBoolean(summary.online());
		}

		public static PlayerSummary readBuf(RegistryByteBuf buf) {
			return new OfflinePlayerSummary(
				null,
				buf.readString(),
				buf.readRegistryKey(RegistryKeys.WORLD),
				buf.readVec3d(),
				buf.readFloat(),
				buf.readBoolean()
			);
		}

		@Override
		public int viewDistance() {
			return 0;
		}

		public record OfflinePlayerExploration(Set<UUID> sharedPlayers, Map<RegistryKey<World>, Map<ChunkPos, BitSet>> terrain, Map<RegistryKey<World>, Map<RegistryKey<Structure>, LongSet>> structures, boolean personal) implements SurveyorExploration {
			public static OfflinePlayerExploration empty(UUID uuid) {
				return new OfflinePlayerExploration(Set.of(uuid), new HashMap<>(), new HashMap<>(), true);
			}

			public static OfflinePlayerExploration ofMerged(Set<SurveyorExploration> explorations) {
				Set<UUID> sharedPlayers = new HashSet<>();
				Map<RegistryKey<World>, Map<ChunkPos, BitSet>> terrain = new HashMap<>();
				Map<RegistryKey<World>, Map<RegistryKey<Structure>, LongSet>> structures = new HashMap<>();
				OfflinePlayerExploration outExploration = new OfflinePlayerExploration(sharedPlayers, terrain, structures, false);
				for (SurveyorExploration exploration : explorations) {
					sharedPlayers.addAll(exploration.sharedPlayers());
					exploration.terrain().forEach((wKey, map) -> map.forEach((rPos, bits) -> outExploration.mergeRegion(wKey, rPos, bits)));
					exploration.structures().forEach((wKey, map) -> map.forEach((sKey, longs) -> outExploration.mergeStructures(wKey, sKey, longs)));
				}
				return outExploration;
			}

			public static SurveyorExploration from(UUID uuid, NbtCompound nbt) {
				OfflinePlayerExploration mutable = new OfflinePlayerExploration(new HashSet<>(Set.of(uuid)), new HashMap<>(), new HashMap<>(), true);
				mutable.read(nbt);
				return mutable;
			}
		}
	}

	class PlayerEntitySummary implements PlayerSummary {
		protected final PlayerEntity player;

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

		public ServerPlayerEntitySummary(ServerPlayerEntity player) {
			super(player);
			this.exploration = new ServerPlayerExploration(player, new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
		}

		@Override
		public SurveyorExploration exploration() {
			return exploration;
		}

		@Override
		public int viewDistance() {
			return ((ServerPlayerEntity) this.player).getViewDistance();
		}

		public void copyExploration(ServerPlayerEntitySummary oldSummary) {
			exploration.copyFrom(oldSummary.exploration);
		}

		public void read(ReadView view) {
			exploration.read(view.read(KEY_DATA, NbtCompound.CODEC).orElse(new NbtCompound()));
		}

		public void writeNbt(WriteView view) {
			NbtCompound surveyorNbt = new NbtCompound();
			exploration.write(surveyorNbt);
			surveyorNbt.putString(PlayerSummary.KEY_USERNAME, username());
			view.put(PlayerSummary.KEY_DATA, NbtCompound.CODEC, surveyorNbt);
		}

		public record ServerPlayerExploration(ServerPlayerEntity player, Map<RegistryKey<World>, Map<ChunkPos, BitSet>> terrain, Map<RegistryKey<World>, Map<RegistryKey<Structure>, LongSet>> structures) implements SurveyorExploration {
			@Override
			public Set<UUID> sharedPlayers() {
				return Set.of(Surveyor.getUuid(player));
			}

			@Override
			public boolean personal() {
				return true;
			}

			@Override
			public void mergeRegion(RegistryKey<World> worldKey, ChunkPos regionPos, BitSet bitSet) { // This method is currently unused for server players, but its implemented anyway
				SurveyorExploration.super.mergeRegion(worldKey, regionPos, bitSet);
				if (player.getServer().isHost(player.getGameProfile())) updateClientForMergeRegion(player.getWorld(), regionPos, bitSet);
				if (Surveyor.CONFIG.networking.terrain.atMost(NetworkMode.SOLO)) return;
				for (ServerPlayerEntity friend : ServerSummary.of(player.getServer()).groupOtherServerPlayers(Surveyor.getUuid(player), player.getServer())) {
					if (friend.getWorld().getRegistryKey().equals(worldKey)) {
						SurveyorExploration friendExploration = SurveyorExploration.of(friend);
						BitSet sendSet = (BitSet) bitSet.clone();
						if (friendExploration.terrain().containsKey(worldKey) && friendExploration.terrain().get(worldKey).containsKey(regionPos)) sendSet.andNot(friendExploration.terrain().get(worldKey).get(regionPos));
						WorldTerrainSummary summary = WorldSummary.of(player.getWorld()).terrain();
						if (!sendSet.isEmpty() && summary != null) S2CUpdateRegionPacket.of(true, regionPos, summary.getRegion(regionPos), sendSet).send(friend);
					}
				}
			}

			@Override
			public void addChunk(RegistryKey<World> worldKey, ChunkPos pos) {
				SurveyorExploration.super.addChunk(worldKey, pos);
				if (player.getServer().isHost(player.getGameProfile())) updateClientForAddChunk(player.getWorld(), pos);
				if (Surveyor.CONFIG.networking.terrain.atMost(NetworkMode.SOLO)) return;
				for (ServerPlayerEntity friend : ServerSummary.of(player.getServer()).groupOtherServerPlayers(Surveyor.getUuid(player), player.getServer())) {
					if (friend.getWorld().getRegistryKey().equals(worldKey) && !SurveyorExploration.of(friend).exploredChunk(worldKey, pos)) {
						ChunkPos regionPos = new ChunkPos(pos.getRegionX(), pos.getRegionZ());
						WorldTerrainSummary summary = WorldSummary.of(player.getServer().getWorld(worldKey)).terrain();
						if (summary == null) continue;
						RegionSummary region = summary.getRegion(regionPos);
						BitSet sendSet = new BitSet();
						sendSet.set(RegionSummary.bitForChunk(pos));
						S2CUpdateRegionPacket.of(true, regionPos, region, sendSet).send(friend);
					}
				}
			}

			@Override
			public void addStructure(RegistryKey<World> worldKey, RegistryKey<Structure> structureKey, ChunkPos pos) {
				SurveyorExploration.super.addStructure(worldKey, structureKey, pos);
				ServerWorld world = player.getWorld();
				if (player.getServer().isHost(player.getGameProfile())) updateClientForAddStructure(world, structureKey, pos);
				WorldStructureSummary summary = WorldSummary.of(world).structures();
				if (Surveyor.CONFIG.networking.structures.atMost(NetworkMode.NONE)) return;
				S2CStructuresAddedPacket.of(false, structureKey, pos, summary).send(player);
				if (Surveyor.CONFIG.networking.structures.atMost(NetworkMode.SOLO)) return;
				for (ServerPlayerEntity friend : ServerSummary.of(player.getServer()).groupOtherServerPlayers(Surveyor.getUuid(player), player.getServer())) {
					if (friend.getWorld().getRegistryKey().equals(worldKey) && !SurveyorExploration.of(friend).exploredStructure(worldKey, structureKey, pos)) {
						S2CStructuresAddedPacket.of(true, structureKey, pos, summary).send(friend);
					}
				}
			}
		}
	}
}
