package folk.sisby.surveyor.packet;

import folk.sisby.surveyor.Surveyor;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;

import java.util.BitSet;
import java.util.Map;

public record C2SKnownTerrainPacket(Map<ChunkPos, BitSet> regionBits) implements C2SPacket {
	public static final CustomPayload.Id<C2SKnownTerrainPacket> ID = new CustomPayload.Id<>(Surveyor.id("known_terrain"));
	public static final PacketCodec<PacketByteBuf, C2SKnownTerrainPacket> CODEC = SurveyorPacketCodecs.TERRAIN_KEYS.xmap(C2SKnownTerrainPacket::new, C2SKnownTerrainPacket::regionBits);

	@Override
	public CustomPayload.Id<C2SKnownTerrainPacket> getId() {
		return ID;
	}
}
