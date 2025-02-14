package folk.sisby.surveyor.packet;

import folk.sisby.surveyor.PlayerSummary;
import folk.sisby.surveyor.Surveyor;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.UUID;

public record S2CGroupUpdatedPacket(Map<UUID, PlayerSummary> players) implements S2CPacket {
	public static final Identifier ID = Surveyor.id("s2c_group_updated");

	public static S2CGroupUpdatedPacket of(UUID uuid, PlayerSummary summary) {
		return new S2CGroupUpdatedPacket(Map.of(uuid, summary));
	}

	public static S2CGroupUpdatedPacket read(PacketByteBuf buf) {
		return new S2CGroupUpdatedPacket(buf.readMap(PacketByteBuf::readUuid, PlayerSummary.OfflinePlayerSummary::readBuf));
	}

	@Override
	public void writeBuf(PacketByteBuf buf) {
		buf.writeMap(players, PacketByteBuf::writeUuid, PlayerSummary.OfflinePlayerSummary::writeBuf);
	}

	@Override
	public Identifier getId() {
		return ID;
	}
}
