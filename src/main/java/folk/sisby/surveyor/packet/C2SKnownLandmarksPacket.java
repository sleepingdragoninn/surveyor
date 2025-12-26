package folk.sisby.surveyor.packet;

import com.google.common.collect.Multimap;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.util.MapUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.Map;
import java.util.UUID;

public record C2SKnownLandmarksPacket(Map<RegistryKey<World>, Multimap<UUID, Identifier>> landmarks) implements C2SPacket {
	public static final Identifier ID = Surveyor.id("c2s_known_landmarks");

	public static C2SPacket of(RegistryKey<World> dimension, Multimap<UUID, Identifier> landmarks) {
		return new C2SKnownLandmarksPacket(Map.of(dimension, landmarks));
	}

	public static C2SKnownLandmarksPacket read(PacketByteBuf buf) {
		return new C2SKnownLandmarksPacket(buf.readMap(
			b -> b.readRegistryKey(RegistryKeys.WORLD),
			b -> MapUtil.asMultiMap(b.readMap(
					PacketByteBuf::readUuid,
					b2 -> b2.readList(PacketByteBuf::readIdentifier)
				)
			)));
	}

	@Override
	public void writeBuf(PacketByteBuf buf) {
		buf.writeMap(
			landmarks,
			PacketByteBuf::writeRegistryKey,
			(b, m) -> b.writeMap(m.asMap(),
				PacketByteBuf::writeUuid,
				(b2, c) -> b2.writeCollection(c, PacketByteBuf::writeIdentifier)
			)
		);
	}

	@Override
	public Identifier getId() {
		return ID;
	}
}
