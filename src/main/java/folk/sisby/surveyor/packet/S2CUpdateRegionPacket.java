package folk.sisby.surveyor.packet;

import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.terrain.ChunkSummary;
import folk.sisby.surveyor.terrain.RegionSummary;
import folk.sisby.surveyor.util.BitSetUtil;
import folk.sisby.surveyor.util.ListUtil;
import folk.sisby.surveyor.util.RegionPos;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;

public record S2CUpdateRegionPacket(RegistryKey<World> dim, boolean shared, RegionPos regionPos, List<Integer> biomePalette, List<Integer> blockPalette, BitSet set, List<ChunkSummary> chunks) implements S2CPacket {
	public static final Identifier ID = Surveyor.id("s2c_update_region");

	public static S2CUpdateRegionPacket of(RegistryKey<World> dim, boolean shared, RegionPos regionPos, RegionSummary summary, BitSet keys) {
		return summary.createUpdatePacket(dim, shared, regionPos, keys);
	}

	public static S2CUpdateRegionPacket read(PacketByteBuf buf) {
		return new S2CUpdateRegionPacket(
			buf.readRegistryKey(RegistryKeys.WORLD),
			buf.readBoolean(),
			RegionPos.of(buf.readLong()),
			buf.readList(PacketByteBuf::readVarInt),
			buf.readList(PacketByteBuf::readVarInt),
			buf.readBitSet(),
			buf.readCollection(ArrayList::new, ChunkSummary::new)
		);
	}

	@Override
	public void writeBuf(PacketByteBuf buf) {
		buf.writeRegistryKey(dim);
		buf.writeBoolean(shared);
		buf.writeLong(regionPos.toLong());
		buf.writeCollection(biomePalette, PacketByteBuf::writeVarInt);
		buf.writeCollection(blockPalette, PacketByteBuf::writeVarInt);
		buf.writeBitSet(set);
		buf.writeCollection(chunks, (b, summary) -> summary.writeBuf(b));
	}

	@Override
	public Collection<PacketByteBuf> toBufs() {
		List<PacketByteBuf> bufs = new ArrayList<>();
		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		writeBuf(buf);
		if (buf.readableBytes() < MAX_PAYLOAD_SIZE) {
			bufs.add(buf);
		} else {
			if (set.cardinality() == 1) {
				int bit = set.stream().findFirst().orElseThrow();
				Surveyor.LOGGER.error("Couldn't create a terrain update packet at {} - an individual chunk would be too large to send!", "[%d,%d]".formatted(regionPos.toChunk(bit).x, regionPos.toChunk(bit).z));
				return List.of();
			}
			for (BitSet splitChunks : BitSetUtil.half(set)) {
				bufs.addAll(new S2CUpdateRegionPacket(dim, shared, regionPos, biomePalette, blockPalette, splitChunks, ListUtil.splitSet(chunks, splitChunks, set)).toBufs());
			}
		}
		return bufs;
	}

	@Override
	public Identifier getId() {
		return ID;
	}
}
