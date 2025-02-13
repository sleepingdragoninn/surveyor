package folk.sisby.surveyor.packet;

import com.google.common.collect.Multimap;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.util.MapUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SyncLandmarksRemovedPacket(Multimap<UUID, Identifier> landmarks) implements SyncPacket {
	public static final Identifier ID = new Identifier(Surveyor.ID, "landmarks_removed");

	public static SyncLandmarksRemovedPacket of(UUID uuid, Identifier id) {
		return new SyncLandmarksRemovedPacket(MapUtil.asMultiMap(Map.of(uuid, List.of(id))));
	}

	public static SyncLandmarksRemovedPacket read(PacketByteBuf buf) {
		return new SyncLandmarksRemovedPacket(MapUtil.asMultiMap(buf.readMap(
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
