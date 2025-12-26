package folk.sisby.surveyor.packet;

import com.google.common.collect.Multimap;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.util.MapUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SyncLandmarksRemovedPacket(RegistryKey<World> dimension, Multimap<UUID, Identifier> landmarks) implements SyncPacket {
	public static final Identifier ID = Surveyor.id("landmarks_removed");

	public static SyncLandmarksRemovedPacket of(RegistryKey<World> dimension, UUID uuid, Identifier id) {
		return new SyncLandmarksRemovedPacket(dimension, MapUtil.asMultiMap(Map.of(uuid, List.of(id))));
	}

	public static SyncLandmarksRemovedPacket read(PacketByteBuf buf) {
		return new SyncLandmarksRemovedPacket(buf.readRegistryKey(RegistryKeys.WORLD), MapUtil.asMultiMap(buf.readMap(
			PacketByteBuf::readUuid,
			b -> b.readList(PacketByteBuf::readIdentifier)
		)));
	}

	@Override
	public void writeBuf(PacketByteBuf buf) {
		buf.writeRegistryKey(dimension);
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
