package folk.sisby.surveyor.packet;

import com.google.common.collect.Multimap;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.util.MapUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.Structure;

import java.util.Arrays;
import java.util.HashSet;

public record C2SKnownStructuresPacket(Multimap<RegistryKey<Structure>, ChunkPos> structureKeys) implements C2SPacket {
	public static final Identifier ID = Surveyor.id("c2s_known_structures");

	public static C2SKnownStructuresPacket read(PacketByteBuf buf) {
		return new C2SKnownStructuresPacket(
			MapUtil.asMultiMap(buf.readMap(b -> b.readRegistryKey(RegistryKeys.STRUCTURE), b -> new HashSet<>(Arrays.stream(b.readLongArray()).mapToObj(ChunkPos::new).toList())))
		);
	}

	@Override
	public void writeBuf(PacketByteBuf buf) {
		buf.writeMap(structureKeys.asMap(), PacketByteBuf::writeRegistryKey, (b, starts) -> b.writeLongArray(starts.stream().mapToLong(ChunkPos::toLong).toArray()));
	}

	@Override
	public Identifier getId() {
		return ID;
	}
}
