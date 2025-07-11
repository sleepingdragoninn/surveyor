package folk.sisby.surveyor.packet;

import com.google.common.collect.Multimap;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.util.MapUtil;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SyncLandmarksRemovedPacket(Multimap<UUID, Identifier> landmarks) implements SyncPacket {
	public static final Id<SyncLandmarksRemovedPacket> ID = new Id<>(Surveyor.id("landmarks_removed"));
	public static final PacketCodec<RegistryByteBuf, SyncLandmarksRemovedPacket> CODEC = PacketCodecs.<RegistryByteBuf, UUID, List<Identifier>, Map<UUID, List<Identifier>>>map(HashMap::new, Uuids.PACKET_CODEC, Identifier.PACKET_CODEC.collect(PacketCodecs.toList()))
		.xmap(MapUtil::asMultiMap, MapUtil::asListMap)
		.xmap(SyncLandmarksRemovedPacket::new, SyncLandmarksRemovedPacket::landmarks);

	public static SyncLandmarksRemovedPacket of(UUID uuid, Identifier id) {
		return new SyncLandmarksRemovedPacket(MapUtil.asMultiMap(Map.of(uuid, List.of(id))));
	}

	@Override
	public Id<SyncLandmarksRemovedPacket> getId() {
		return ID;
	}
}
