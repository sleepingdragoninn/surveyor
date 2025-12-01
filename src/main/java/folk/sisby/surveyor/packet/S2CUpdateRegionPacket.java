package folk.sisby.surveyor.packet;

import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.terrain.ChunkSummary;
import folk.sisby.surveyor.terrain.RegionSummary;
import folk.sisby.surveyor.util.BitSetUtil;
import folk.sisby.surveyor.util.ListUtil;
import folk.sisby.surveyor.util.RegionPos;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.dynamic.Codecs;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public record S2CUpdateRegionPacket(boolean shared, RegionPos regionPos, List<Integer> biomePalette, List<Integer> blockPalette, BitSet set, List<ChunkSummary> chunks) implements S2CPacket {
	public static final CustomPayload.Id<S2CUpdateRegionPacket> ID = new CustomPayload.Id<>(Surveyor.id("s2c_update_region"));
	public static final PacketCodec<PacketByteBuf, S2CUpdateRegionPacket> CODEC = PacketCodec.tuple(
		PacketCodecs.BOOL, S2CUpdateRegionPacket::shared,
		RegionPos.PACKET_CODEC, S2CUpdateRegionPacket::regionPos,
		PacketCodecs.INTEGER.collect(PacketCodecs.toList()), S2CUpdateRegionPacket::biomePalette,
		PacketCodecs.INTEGER.collect(PacketCodecs.toList()), S2CUpdateRegionPacket::blockPalette,
		PacketCodecs.codec(Codecs.BIT_SET), S2CUpdateRegionPacket::set,
		PacketCodec.of(ChunkSummary::writeBuf, ChunkSummary::new).collect(PacketCodecs.toList()), S2CUpdateRegionPacket::chunks,
		S2CUpdateRegionPacket::new
	);

	public static S2CUpdateRegionPacket of(boolean shared, RegionPos regionPos, RegionSummary summary, BitSet keys, DynamicRegistryManager manager) {
		return summary.createUpdatePacket(shared, regionPos, keys, manager);
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
				Surveyor.LOGGER.error("Couldn't create a terrain update packet at {} - an individual chunk would be too large to send!", "[%d,%d]".formatted(regionPos.toChunk(bit).x, regionPos.toChunk(bit).z));
				return List.of();
			}
			for (BitSet splitChunks : BitSetUtil.half(set)) {
				payloads.addAll(new S2CUpdateRegionPacket(shared, regionPos, biomePalette, blockPalette, splitChunks, ListUtil.splitSet(chunks, splitChunks, set)).toPayloads());
			}
		}
		return payloads;
	}

	@Override
	public CustomPayload.Id<S2CUpdateRegionPacket> getId() {
		return ID;
	}
}
