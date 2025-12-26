package folk.sisby.surveyor.packet;

import com.google.common.collect.Multimap;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.util.MapUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

public record C2SKnownStructuresPacket(Map<RegistryKey<World>, Multimap<RegistryKey<Structure>, ChunkPos>> starts) implements C2SPacket {
	public static final Identifier ID = Surveyor.id("c2s_known_structures");

	public static C2SKnownStructuresPacket read(PacketByteBuf buf) {
		return new C2SKnownStructuresPacket(buf.readMap(
			b -> b.readRegistryKey(RegistryKeys.WORLD),
			b -> MapUtil.asMultiMap(b.readMap(
				b2 -> b2.readRegistryKey(RegistryKeys.STRUCTURE),
				b2 -> new HashSet<>(Arrays.stream(b2.readLongArray()).mapToObj(ChunkPos::new).toList()))
			)
		));
	}

	@Override
	public void writeBuf(PacketByteBuf buf) {
		buf.writeMap(
			starts,
			PacketByteBuf::writeRegistryKey,
			(b, m) -> b.writeMap(m.asMap(),
				PacketByteBuf::writeRegistryKey,
				(b2, starts) -> b2.writeLongArray(starts.stream().mapToLong(ChunkPos::toLong).toArray())
			)
		);
	}

	@Override
	public Identifier getId() {
		return ID;
	}
}
