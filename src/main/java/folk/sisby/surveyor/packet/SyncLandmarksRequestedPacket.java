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

public record SyncLandmarksRequestedPacket(RegistryKey<World> dim, Multimap<UUID, Identifier> landmarks) implements SyncPacket {
	public static final Identifier ID = Surveyor.id("landmarks_requested");

	public static SyncLandmarksRequestedPacket of(RegistryKey<World> dim, UUID uuid, Identifier id) {
		return new SyncLandmarksRequestedPacket(dim, MapUtil.asMultiMap(Map.of(uuid, List.of(id))));
	}

	public static SyncLandmarksRequestedPacket read(PacketByteBuf buf) {
		return new SyncLandmarksRequestedPacket(buf.readRegistryKey(RegistryKeys.WORLD), MapUtil.asMultiMap(buf.readMap(
			PacketByteBuf::readUuid,
			b -> b.readList(PacketByteBuf::readIdentifier)
		)));
	}

	@Override
	public void writeBuf(PacketByteBuf buf) {
		buf.writeRegistryKey(dim);
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
