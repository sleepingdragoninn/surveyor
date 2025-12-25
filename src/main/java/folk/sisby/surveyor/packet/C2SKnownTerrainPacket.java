package folk.sisby.surveyor.packet;

import com.google.common.collect.Table;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.util.MapUtil;
import folk.sisby.surveyor.util.RegionPos;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.BitSet;
import java.util.Map;

public record C2SKnownTerrainPacket(Table<RegistryKey<World>, RegionPos, BitSet> regionBits) implements C2SPacket {
	public static final Identifier ID = Surveyor.id("known_terrain");

	public static C2SKnownTerrainPacket read(PacketByteBuf buf) {
		return new C2SKnownTerrainPacket(MapUtil.asTable(buf.readMap(b -> b.readRegistryKey(RegistryKeys.WORLD),
			b -> b.readMap(b2 -> RegionPos.of(b2.readLong()), PacketByteBuf::readBitSet)
		)));
	}

	@Override
	public void writeBuf(PacketByteBuf buf) {
		buf.writeMap(regionBits.rowMap(),
			PacketByteBuf::writeRegistryKey,
			(b, m) -> b.writeMap(m,
				(b2, r) -> b2.writeLong(r.toLong()),
				PacketByteBuf::writeBitSet
			)
		);
	}

	@Override
	public Identifier getId() {
		return ID;
	}
}
