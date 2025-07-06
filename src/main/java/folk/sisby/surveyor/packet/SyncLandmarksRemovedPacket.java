package folk.sisby.surveyor.packet;

import com.google.common.collect.Multimap;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.landmark.LandmarkType;
import folk.sisby.surveyor.util.MapUtil;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record SyncLandmarksRemovedPacket(Multimap<LandmarkType<?>, BlockPos> landmarks) implements SyncPacket {
	public static final Id<SyncLandmarksRemovedPacket> ID = new Id<>(Identifier.of(Surveyor.ID, "landmarks_removed"));
	public static final PacketCodec<RegistryByteBuf, SyncLandmarksRemovedPacket> CODEC = PacketCodecs.<RegistryByteBuf, LandmarkType<?>, List<BlockPos>, Map<LandmarkType<?>, List<BlockPos>>>map(HashMap::new, PacketCodecs.codec(LandmarkType.CODEC), BlockPos.PACKET_CODEC.collect(PacketCodecs.toList()))
		.xmap(MapUtil::asMultiMap, MapUtil::asListMap)
		.xmap(SyncLandmarksRemovedPacket::new, SyncLandmarksRemovedPacket::landmarks);

	public static SyncLandmarksRemovedPacket of(LandmarkType<?> type, BlockPos pos) {
		return new SyncLandmarksRemovedPacket(MapUtil.asMultiMap(Map.of(type, List.of(pos))));
	}

	@Override
	public Id<SyncLandmarksRemovedPacket> getId() {
		return ID;
	}
}
