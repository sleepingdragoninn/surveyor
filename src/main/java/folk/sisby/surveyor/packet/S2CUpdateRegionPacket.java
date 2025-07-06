package folk.sisby.surveyor.packet;

import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.terrain.ChunkSummary;
import folk.sisby.surveyor.terrain.RegionSummary;
import folk.sisby.surveyor.util.BitSetUtil;
import folk.sisby.surveyor.util.ListUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public record S2CUpdateRegionPacket(boolean shared, ChunkPos regionPos, List<Integer> biomePalette, List<Integer> blockPalette, BitSet set, List<ChunkSummary> chunks) implements S2CPacket {
	public static final Id<S2CUpdateRegionPacket> ID = new Id<>(Identifier.of(Surveyor.ID, "s2c_update_region"));
	public static final PacketCodec<PacketByteBuf, S2CUpdateRegionPacket> CODEC = PacketCodec.tuple(
		PacketCodecs.BOOLEAN, S2CUpdateRegionPacket::shared,
		PacketCodecs.VAR_LONG.xmap(ChunkPos::new, ChunkPos::toLong), S2CUpdateRegionPacket::regionPos,
		PacketCodecs.INTEGER.collect(PacketCodecs.toList()), S2CUpdateRegionPacket::biomePalette,
		PacketCodecs.INTEGER.collect(PacketCodecs.toList()), S2CUpdateRegionPacket::blockPalette,
		PacketCodecs.codec(Codecs.BIT_SET), S2CUpdateRegionPacket::set,
		PacketCodec.of(ChunkSummary::writeBuf, ChunkSummary::new).collect(PacketCodecs.toList()), S2CUpdateRegionPacket::chunks,
		S2CUpdateRegionPacket::new
	);

	public static S2CUpdateRegionPacket of(boolean shared, ChunkPos regionPos, RegionSummary summary, BitSet keys) {
		return summary.createUpdatePacket(shared, regionPos, keys);
	}

	@Override
	public List<SurveyorPacket> toPayloads() {
		List<SurveyorPacket> payloads = new ArrayList<>();
		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		CODEC.encode(buf, this);
		if (buf.readableBytes() < MAX_PAYLOAD_SIZE) {
			payloads.add(this);
		} else {
			if (set.cardinality() == 1) {
				int bit = set.stream().findFirst().orElseThrow();
				Surveyor.LOGGER.error("Couldn't create a terrain update packet at {} - an individual chunk would be too large to send!", "[%d,%d]".formatted(regionPos.x + RegionSummary.xForBit(bit), regionPos.z + RegionSummary.zForBit(bit)));
				return List.of();
			}
			for (BitSet splitChunks : BitSetUtil.half(set)) {
				payloads.addAll(new S2CUpdateRegionPacket(shared, regionPos, biomePalette, blockPalette, splitChunks, ListUtil.splitSet(chunks, splitChunks, set)).toPayloads());
			}
		}
		return payloads;
	}

	@Override
	public Id<S2CUpdateRegionPacket> getId() {
		return ID;
	}
}
