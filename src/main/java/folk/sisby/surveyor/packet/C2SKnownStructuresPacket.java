package folk.sisby.surveyor.packet;

import com.google.common.collect.Multimap;
import folk.sisby.surveyor.Surveyor;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;

import java.util.Map;

public record C2SKnownStructuresPacket(Map<RegistryKey<World>, Multimap<RegistryKey<Structure>, ChunkPos>> starts) implements C2SPacket {
	public static final CustomPayload.Id<C2SKnownStructuresPacket> ID = new CustomPayload.Id<>(Surveyor.id("c2s_known_structures"));
	public static final PacketCodec<PacketByteBuf, C2SKnownStructuresPacket> CODEC = SurveyorPacketCodecs.STRUCTURE_KEYS.xmap(C2SKnownStructuresPacket::new, C2SKnownStructuresPacket::starts);

	@Override
	public CustomPayload.Id<C2SKnownStructuresPacket> getId() {
		return ID;
	}
}
