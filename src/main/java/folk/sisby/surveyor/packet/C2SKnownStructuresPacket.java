package folk.sisby.surveyor.packet;

import com.google.common.collect.Multimap;
import folk.sisby.surveyor.Surveyor;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.Structure;

public record C2SKnownStructuresPacket(Multimap<RegistryKey<Structure>, ChunkPos> structureKeys) implements C2SPacket {
	public static final Id<C2SKnownStructuresPacket> ID = new Id<>(Identifier.of(Surveyor.ID, "c2s_known_structures"));
	public static final PacketCodec<PacketByteBuf, C2SKnownStructuresPacket> CODEC = SurveyorPacketCodecs.STRUCTURE_KEYS.xmap(C2SKnownStructuresPacket::new, C2SKnownStructuresPacket::structureKeys);

	@Override
	public Id<C2SKnownStructuresPacket> getId() {
		return ID;
	}
}
