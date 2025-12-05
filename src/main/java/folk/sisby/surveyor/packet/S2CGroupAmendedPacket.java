package folk.sisby.surveyor.packet;

import folk.sisby.surveyor.Surveyor;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * for players new to the server with no data associated
 */
public record S2CGroupAmendedPacket(UUID player) implements S2CPacket {
	public static final Identifier ID = Surveyor.id("s2c_group_amended");

	public static S2CGroupAmendedPacket read(PacketByteBuf buf) {
		return new S2CGroupAmendedPacket(buf.readUuid());
	}

	@Override
	public void writeBuf(PacketByteBuf buf) {
		buf.writeUuid(player);
	}

	@Override
	public Identifier getId() {
		return ID;
	}
}
