package folk.sisby.surveyor.packet;

import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.util.RegionPos;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.BitSet;
import java.util.Map;

public record C2SKnownTerrainPacket(Map<RegionPos, BitSet> regionBits) implements C2SPacket {
	public static final Identifier ID = Surveyor.id("known_terrain");

	public static C2SKnownTerrainPacket read(PacketByteBuf buf) {
		return new C2SKnownTerrainPacket(
			buf.readMap(b -> RegionPos.of(b.readLong()), PacketByteBuf::readBitSet)
		);
	}

	@Override
	public void writeBuf(PacketByteBuf buf) {
		buf.writeMap(regionBits, (b, r) -> b.writeLong(r.toLong()), PacketByteBuf::writeBitSet);
	}

	@Override
	public Identifier getId() {
		return ID;
	}
}
