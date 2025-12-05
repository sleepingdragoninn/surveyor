package folk.sisby.surveyor.packet;

import folk.sisby.surveyor.Surveyor;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;

import java.util.UUID;

/**
 * for players new to the server with no data associated
 */
public record S2CGroupAmendedPacket(UUID player) implements S2CPacket {
	public static final CustomPayload.Id<S2CGroupAmendedPacket> ID = new CustomPayload.Id<>(Surveyor.id("s2c_group_amended"));
	public static final PacketCodec<ByteBuf, S2CGroupAmendedPacket> CODEC = Uuids.PACKET_CODEC.xmap(S2CGroupAmendedPacket::new, S2CGroupAmendedPacket::player);

	@Override
	public Id<S2CGroupAmendedPacket> getId() {
		return ID;
	}
}
