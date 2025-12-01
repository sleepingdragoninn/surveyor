package folk.sisby.surveyor.packet;

import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.util.RegionPos;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;

import java.util.BitSet;
import java.util.Map;

public record C2SKnownTerrainPacket(Map<RegionPos, BitSet> regionBits) implements C2SPacket {
	public static final Id<C2SKnownTerrainPacket> ID = new Id<>(Surveyor.id("known_terrain"));
	public static final PacketCodec<RegistryByteBuf, C2SKnownTerrainPacket> CODEC = SurveyorPacketCodecs.TERRAIN_KEYS.xmap(C2SKnownTerrainPacket::new, C2SKnownTerrainPacket::regionBits);

	@Override
	public Id<C2SKnownTerrainPacket> getId() {
		return ID;
	}
}
