package folk.sisby.surveyor.packet;

import folk.sisby.surveyor.PlayerSummary;
import folk.sisby.surveyor.Surveyor;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.Structure;

import java.util.BitSet;
import java.util.Map;
import java.util.UUID;

public record S2CGroupChangedPacket(Map<UUID, PlayerSummary> players, Map<ChunkPos, BitSet> regionBits, Map<RegistryKey<Structure>, LongSet> structureKeys) implements S2CPacket {
	public static final CustomPayload.Id<S2CGroupChangedPacket> ID = new CustomPayload.Id<>(Surveyor.id("s2c_group_changed"));
	public static final PacketCodec<RegistryByteBuf, S2CGroupChangedPacket> CODEC = PacketCodec.tuple(
		SurveyorPacketCodecs.GROUP_SUMMARIES, S2CGroupChangedPacket::players,
		SurveyorPacketCodecs.TERRAIN_KEYS, S2CGroupChangedPacket::regionBits,
		SurveyorPacketCodecs.STRUCTURE_KEYS_LONG_SET, S2CGroupChangedPacket::structureKeys,
		S2CGroupChangedPacket::new
	);

	@Override
	public CustomPayload.Id<S2CGroupChangedPacket> getId() {
		return ID;
	}
}
