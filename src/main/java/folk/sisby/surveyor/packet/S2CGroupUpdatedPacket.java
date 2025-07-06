package folk.sisby.surveyor.packet;

import folk.sisby.surveyor.PlayerSummary;
import folk.sisby.surveyor.Surveyor;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.UUID;

public record S2CGroupUpdatedPacket(Map<UUID, PlayerSummary> players) implements S2CPacket {
	public static final Id<S2CGroupUpdatedPacket> ID = new Id<>(Identifier.of(Surveyor.ID, "s2c_group_updated"));
	public static final PacketCodec<RegistryByteBuf, S2CGroupUpdatedPacket> CODEC = SurveyorPacketCodecs.GROUP_SUMMARIES.xmap(S2CGroupUpdatedPacket::new, S2CGroupUpdatedPacket::players);

	public static S2CGroupUpdatedPacket of(UUID uuid, PlayerSummary summary) {
		return new S2CGroupUpdatedPacket(Map.of(uuid, summary));
	}

	@Override
	public Id<S2CGroupUpdatedPacket> getId() {
		return ID;
	}
}
