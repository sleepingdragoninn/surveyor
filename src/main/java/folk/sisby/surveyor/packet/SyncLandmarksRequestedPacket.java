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

public record SyncLandmarksRequestedPacket(RegistryKey<World> dimension, Multimap<UUID, Identifier> landmarks) implements SyncPacket {
	public static final CustomPayload.Id<SyncLandmarksRequestedPacket> ID = new CustomPayload.Id<>(Surveyor.id("landmarks_requested"));
	public static final PacketCodec<RegistryByteBuf, SyncLandmarksRequestedPacket> CODEC = PacketCodec.tuple(
		RegistryKey.createPacketCodec(RegistryKeys.WORLD), SyncLandmarksRequestedPacket::dimension,
		PacketCodecs.<RegistryByteBuf, UUID, List<Identifier>, Map<UUID, List<Identifier>>>map(HashMap::new, Uuids.PACKET_CODEC, Identifier.PACKET_CODEC.collect(PacketCodecs.toList())).xmap(MapUtil::asMultiMap, MapUtil::asListMap), SyncLandmarksRequestedPacket::landmarks,
		SyncLandmarksRequestedPacket::new
	);

	public static SyncLandmarksRequestedPacket of(RegistryKey<World> dimension, UUID uuid, Identifier id) {
		return new SyncLandmarksRequestedPacket(dimension, MapUtil.asMultiMap(Map.of(uuid, List.of(id))));
	}

	@Override
	public Id<SyncLandmarksRequestedPacket> getId() {
		return ID;
	}
}
