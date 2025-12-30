package folk.sisby.surveyor.packet;

import com.google.common.collect.Multimap;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.util.MapUtil;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SyncLandmarksRemovedPacket(RegistryKey<World> dimension, Multimap<UUID, Identifier> landmarks) implements SyncPacket {
	public static final Id<SyncLandmarksRemovedPacket> ID = new Id<>(Surveyor.id("landmarks_removed"));
	public static final PacketCodec<RegistryByteBuf, SyncLandmarksRemovedPacket> CODEC = PacketCodec.tuple(
		RegistryKey.createPacketCodec(RegistryKeys.WORLD), SyncLandmarksRemovedPacket::dimension,
		PacketCodecs.<RegistryByteBuf, UUID, List<Identifier>, Map<UUID, List<Identifier>>>map(HashMap::new, Uuids.PACKET_CODEC, Identifier.PACKET_CODEC.collect(PacketCodecs.toList())).xmap(MapUtil::asMultiMap, MapUtil::asListMap), SyncLandmarksRemovedPacket::landmarks,
		SyncLandmarksRemovedPacket::new
	);

	public static SyncLandmarksRemovedPacket of(RegistryKey<World> dimension, UUID uuid, Identifier id) {
		return new SyncLandmarksRemovedPacket(dimension, MapUtil.asMultiMap(Map.of(uuid, List.of(id))));
	}

	@Override
	public Id<SyncLandmarksRemovedPacket> getId() {
		return ID;
	}
}
