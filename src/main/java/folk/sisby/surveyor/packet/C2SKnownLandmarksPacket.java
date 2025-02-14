package folk.sisby.surveyor.packet;

import com.google.common.collect.Multimap;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.util.MapUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record C2SKnownLandmarksPacket(Multimap<UUID, Identifier> landmarks) implements C2SPacket {
	public static final Identifier ID = Surveyor.id("c2s_known_landmarks");

	public static C2SKnownLandmarksPacket read(PacketByteBuf buf) {
		return new C2SKnownLandmarksPacket(MapUtil.asMultiMap(buf.readMap(
			PacketByteBuf::readUuid,
			b -> b.readList(PacketByteBuf::readIdentifier)
		)));
	}

	@Override
	public void writeBuf(PacketByteBuf buf) {
		buf.writeMap(landmarks.asMap(),
			PacketByteBuf::writeUuid,
			(b, c) -> b.writeCollection(c, PacketByteBuf::writeIdentifier)
		);
	}

	@Override
	public Identifier getId() {
		return ID;
	}
}
