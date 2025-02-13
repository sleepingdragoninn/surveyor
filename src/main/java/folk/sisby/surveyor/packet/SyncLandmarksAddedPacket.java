package folk.sisby.surveyor.packet;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.util.MapUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SyncLandmarksAddedPacket(Map<UUID, Map<Identifier, Landmark>> landmarks) implements SyncPacket {
	public static final CustomPayload.Id<SyncLandmarksAddedPacket> ID = new CustomPayload.Id<>(Identifier.of(Surveyor.ID, "landmarks_added"));
	public static final PacketCodec<ByteBuf, SyncLandmarksAddedPacket> CODEC = SurveyorPacketCodecs.LANDMARK_SUMMARIES.xmap(SyncLandmarksAddedPacket::new, SyncLandmarksAddedPacket::landmarks);

	public static SyncLandmarksAddedPacket of(Multimap<UUID, Identifier> keySet, WorldLandmarks summary) {
		return summary.createUpdatePacket(keySet);
	}

	@Override
	public List<SurveyorPacket> toPayloads() {
		List<SurveyorPacket> payloads = new ArrayList<>();
		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		CODEC.encode(buf, this);
		if (buf.readableBytes() < MAX_PAYLOAD_SIZE) {
			payloads.add(this);
		} else {
			Multimap<UUID, Identifier> keySet = MapUtil.keyMultiMap(landmarks);
			if (keySet.size() == 1) {
				Surveyor.LOGGER.error("Couldn't create a landmark update packet for {} at {} - an individual landmark would be too large to send!", keySet.keys().stream().findFirst().orElseThrow(), keySet.values().stream().findFirst().orElseThrow());
				return List.of();
			}
			Multimap<UUID, Identifier> firstHalf = HashMultimap.create();
			Multimap<UUID, Identifier> secondHalf = HashMultimap.create();
			keySet.forEach((key, pos) -> {
				if (firstHalf.size() < keySet.size() / 2) {
					firstHalf.put(key, pos);
				} else {
					secondHalf.put(key, pos);
				}
			});
			payloads.addAll(new SyncLandmarksAddedPacket(MapUtil.splitByKeyMap(landmarks, firstHalf)).toPayloads());
			payloads.addAll(new SyncLandmarksAddedPacket(MapUtil.splitByKeyMap(landmarks, secondHalf)).toPayloads());
		}
		return payloads;
	}

	@Override
	public Id<SyncLandmarksAddedPacket> getId() {
		return ID;
	}
}
