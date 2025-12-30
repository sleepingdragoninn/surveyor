package folk.sisby.surveyor.packet;

import com.google.common.collect.Table;
import folk.sisby.surveyor.PlayerSummary;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.util.MapUtil;
import folk.sisby.surveyor.util.RegionPos;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;

import java.util.BitSet;
import java.util.Map;
import java.util.UUID;

public record S2CGroupChangedPacket(Map<UUID, PlayerSummary> players, Table<RegistryKey<World>, RegionPos, BitSet> chunks, Table<RegistryKey<World>, RegistryKey<Structure>, LongSet> starts) implements S2CPacket {
	public static final Identifier ID = Surveyor.id("s2c_group_changed");

	public static S2CGroupChangedPacket read(PacketByteBuf buf) {
		return new S2CGroupChangedPacket(
			buf.readMap(PacketByteBuf::readUuid, PlayerSummary.OfflinePlayerSummary::readBuf),
			MapUtil.asTable(buf.readMap(b -> b.readRegistryKey(RegistryKeys.WORLD),
				b -> b.readMap(b2 -> RegionPos.of(b2.readLong()), PacketByteBuf::readBitSet)
			)),
			MapUtil.asTable(buf.readMap(
				b -> b.readRegistryKey(RegistryKeys.WORLD),
				b -> b.readMap(
					b2 -> b2.readRegistryKey(RegistryKeys.STRUCTURE),
					b2 -> LongSet.of(b2.readLongArray())
				)
			))
		);
	}

	@Override
	public void writeBuf(PacketByteBuf buf) {
		buf.writeMap(players, PacketByteBuf::writeUuid, PlayerSummary.OfflinePlayerSummary::writeBuf);
		buf.writeMap(chunks.rowMap(),
			PacketByteBuf::writeRegistryKey,
			(b, m) -> b.writeMap(m,
				(b2, r) -> b2.writeLong(r.toLong()),
				PacketByteBuf::writeBitSet
			));
		buf.writeMap(starts.rowMap(),
			PacketByteBuf::writeRegistryKey,
			(b, m) -> b.writeMap(m,
				PacketByteBuf::writeRegistryKey,
				(b2, starts) -> b2.writeLongArray(starts.toLongArray())
			)
		);
	}

	@Override
	public Identifier getId() {
		return ID;
	}
}
