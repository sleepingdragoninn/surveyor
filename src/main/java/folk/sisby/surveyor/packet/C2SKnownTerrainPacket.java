package folk.sisby.surveyor.packet;

import com.google.common.collect.Table;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.util.RegionPos;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.BitSet;

public record C2SKnownTerrainPacket(Table<RegistryKey<World>, RegionPos, BitSet> chunks) implements C2SPacket {
	public static final CustomPayload.Id<C2SKnownTerrainPacket> ID = new CustomPayload.Id<>(Surveyor.id("known_terrain"));
	public static final PacketCodec<PacketByteBuf, C2SKnownTerrainPacket> CODEC = SurveyorPacketCodecs.TERRAIN_KEYS.xmap(C2SKnownTerrainPacket::new, C2SKnownTerrainPacket::chunks);

	@Override
	public CustomPayload.Id<C2SKnownTerrainPacket> getId() {
		return ID;
	}
}
