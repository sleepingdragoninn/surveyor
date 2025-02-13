package folk.sisby.surveyor.packet;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.Landmarks;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.util.MapUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SyncLandmarksAddedPacket(Map<UUID, Map<Identifier, Landmark>> landmarks) implements SyncPacket {
	public static final Identifier ID = new Identifier(Surveyor.ID, "landmarks_added");

	public static SyncLandmarksAddedPacket of(Multimap<UUID, Identifier> keySet, WorldLandmarks summary) {
		return summary.createUpdatePacket(keySet);
	}

	public static SyncLandmarksAddedPacket read(PacketByteBuf buf) {
		return new SyncLandmarksAddedPacket(buf.readMap(
			PacketByteBuf::readUuid,
			b -> b.readMap(PacketByteBuf::readIdentifier, b2 -> Landmarks.CODEC.decode(NbtOps.INSTANCE, b2.readNbt()).getOrThrow(false, Surveyor.LOGGER::error).getFirst().values().stream().findFirst().orElseThrow().values().stream().findFirst().orElseThrow())
		));
	}

	@Override
	public void writeBuf(PacketByteBuf buf) {
		buf.writeMap(landmarks,
			PacketByteBuf::writeUuid,
			(b, m) -> b.writeMap(m, PacketByteBuf::writeIdentifier, (b2, landmark) -> b2.writeNbt((NbtCompound) Landmarks.CODEC.encodeStart(NbtOps.INSTANCE, Map.of(landmark.owner(), Map.of(landmark.id(), landmark))).getOrThrow(false, Surveyor.LOGGER::error)))
		);
	}

	@Override
	public Collection<PacketByteBuf> toBufs() {
		List<PacketByteBuf> bufs = new ArrayList<>();
		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		writeBuf(buf);
		if (buf.readableBytes() < MAX_PAYLOAD_SIZE) {
			bufs.add(buf);
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
			bufs.addAll(new SyncLandmarksAddedPacket(MapUtil.splitByKeyMap(landmarks, firstHalf)).toBufs());
			bufs.addAll(new SyncLandmarksAddedPacket(MapUtil.splitByKeyMap(landmarks, secondHalf)).toBufs());
		}
		return bufs;
	}

	@Override
	public Identifier getId() {
		return ID;
	}
}
