package folk.sisby.surveyor.packet;

import folk.sisby.surveyor.Surveyor;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;

import java.util.BitSet;
import java.util.Map;

public record C2SKnownTerrainPacket(Map<ChunkPos, BitSet> regionBits) implements C2SPacket {
	public static final Identifier ID = Surveyor.id("known_terrain");

	public static C2SKnownTerrainPacket read(PacketByteBuf buf) {
		return new C2SKnownTerrainPacket(
			buf.readMap(PacketByteBuf::readChunkPos, PacketByteBuf::readBitSet)
		);
	}

	@Override
	public void writeBuf(PacketByteBuf buf) {
		buf.writeMap(regionBits, PacketByteBuf::writeChunkPos, PacketByteBuf::writeBitSet);
	}

	@Override
	public Identifier getId() {
		return ID;
	}
}
