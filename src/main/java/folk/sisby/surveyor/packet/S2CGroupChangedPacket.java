package folk.sisby.surveyor.packet;

import com.google.common.collect.Table;
import folk.sisby.surveyor.PlayerSummary;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.util.RegionPos;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;

import java.util.BitSet;
import java.util.Map;
import java.util.UUID;

public record S2CGroupChangedPacket(Map<UUID, PlayerSummary> players, Table<RegistryKey<World>, RegionPos, BitSet> chunks, Table<RegistryKey<World>, RegistryKey<Structure>, LongSet> starts) implements S2CPacket {
	public static final CustomPayload.Id<S2CGroupChangedPacket> ID = new CustomPayload.Id<>(Surveyor.id("s2c_group_changed"));
	public static final PacketCodec<RegistryByteBuf, S2CGroupChangedPacket> CODEC = PacketCodec.tuple(
		SurveyorPacketCodecs.GROUP_SUMMARIES, S2CGroupChangedPacket::players,
		SurveyorPacketCodecs.TERRAIN_KEYS, S2CGroupChangedPacket::chunks,
		SurveyorPacketCodecs.STRUCTURE_KEYS_LONG_SET, S2CGroupChangedPacket::starts,
		S2CGroupChangedPacket::new
	);

	@Override
	public CustomPayload.Id<S2CGroupChangedPacket> getId() {
		return ID;
	}
}
