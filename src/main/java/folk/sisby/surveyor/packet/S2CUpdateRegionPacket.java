package folk.sisby.surveyor.packet;

import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.terrain.ChunkSummary;
import folk.sisby.surveyor.terrain.RegionSummary;
import folk.sisby.surveyor.util.BitSetUtil;
import folk.sisby.surveyor.util.ListUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;

public record S2CUpdateRegionPacket(boolean shared, ChunkPos regionPos, List<Integer> biomePalette, List<Integer> blockPalette, BitSet set, List<ChunkSummary> chunks) implements S2CPacket {
	public static final Identifier ID = Surveyor.id("s2c_update_region");

	public static S2CUpdateRegionPacket of(boolean shared, ChunkPos regionPos, RegionSummary summary, BitSet keys, DynamicRegistryManager manager) {
		return summary.createUpdatePacket(shared, regionPos, keys, manager);
	}

	public static S2CUpdateRegionPacket read(PacketByteBuf buf) {
		return new S2CUpdateRegionPacket(
			buf.readBoolean(),
			buf.readChunkPos(),
			buf.readList(PacketByteBuf::readVarInt),
			buf.readList(PacketByteBuf::readVarInt),
			buf.readBitSet(),
			buf.readCollection(ArrayList::new, ChunkSummary::new)
		);
	}

	@Override
	public void writeBuf(PacketByteBuf buf) {
		buf.writeBoolean(shared);
		buf.writeChunkPos(regionPos);
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
				Surveyor.LOGGER.error("Couldn't create a terrain update packet at {} - an individual chunk would be too large to send!", "[%d,%d]".formatted(regionPos.x + RegionSummary.xForBit(bit), regionPos.z + RegionSummary.zForBit(bit)));
				return List.of();
			}
			for (BitSet splitChunks : BitSetUtil.half(set)) {
				bufs.addAll(new S2CUpdateRegionPacket(shared, regionPos, biomePalette, blockPalette, splitChunks, ListUtil.splitSet(chunks, splitChunks, set)).toBufs());
			}
		}
		return bufs;
	}

	@Override
	public Identifier getId() {
		return ID;
	}
}
