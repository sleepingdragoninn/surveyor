package folk.sisby.surveyor.packet;

import com.mojang.datafixers.util.Pair;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.terrain.ChunkSummary;
import folk.sisby.surveyor.terrain.RegionSummary;
import folk.sisby.surveyor.util.BitSetUtil;
import folk.sisby.surveyor.util.ListUtil;
import folk.sisby.surveyor.util.RegionPos;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public record S2CUpdateRegionPacket(RegistryKey<World> dimension, boolean shared, RegionPos regionPos, List<Integer> biomePalette, List<Integer> blockPalette, BitSet set, List<ChunkSummary> chunks) implements S2CPacket, ShareFlagged<S2CUpdateRegionPacket> {
	public static final Id<S2CUpdateRegionPacket> ID = new Id<>(Surveyor.id("s2c_update_region"));
	public static final PacketCodec<RegistryByteBuf, S2CUpdateRegionPacket> CODEC = PacketCodec.tuple(
		PacketCodec.tuple(
			RegistryKey.createPacketCodec(RegistryKeys.WORLD), Pair<RegistryKey<World>, Boolean>::getFirst,
			PacketCodecs.BOOLEAN, Pair<RegistryKey<World>, Boolean>::getSecond,
			Pair::new
		), p -> new Pair<>(p.dimension(), p.shared()),
		RegionPos.PACKET_CODEC, S2CUpdateRegionPacket::regionPos,
		PacketCodecs.INTEGER.collect(PacketCodecs.toList()), S2CUpdateRegionPacket::biomePalette,
		PacketCodecs.INTEGER.collect(PacketCodecs.toList()), S2CUpdateRegionPacket::blockPalette,
		PacketCodecs.codec(Codecs.BIT_SET), S2CUpdateRegionPacket::set,
		PacketCodec.of(ChunkSummary::writeBuf, ChunkSummary::new).collect(PacketCodecs.toList()), S2CUpdateRegionPacket::chunks,
		(pair, rp, bi, bl, s, c) -> new S2CUpdateRegionPacket(pair.getFirst(), pair.getSecond(), rp, bi, bl, s, c)
	);

	public static S2CUpdateRegionPacket of(RegistryKey<World> dimension, boolean shared, RegionPos regionPos, RegionSummary summary, BitSet keys) {
		return summary.createUpdatePacket(dimension, shared, regionPos, keys);
	}

	@Override
	public S2CUpdateRegionPacket withShared(boolean shared) {
		return new S2CUpdateRegionPacket(dimension, shared, regionPos, biomePalette, blockPalette, set, chunks);
	}

	@Override
	public List<SurveyorPacket> toPayloads(DynamicRegistryManager registryManager) {
		List<SurveyorPacket> payloads = new ArrayList<>();
		RegistryByteBuf buf = new RegistryByteBuf(new PacketByteBuf(Unpooled.buffer()), registryManager);
		CODEC.encode(buf, this);
		if (buf.readableBytes() < MAX_PAYLOAD_SIZE) {
			payloads.add(this);
		} else {
			if (set.cardinality() == 1) {
				int bit = set.stream().findFirst().orElseThrow();
				Surveyor.LOGGER.error("Couldn't create a terrain update packet at {} - an individual chunk would be too large to send!", "[%d,%d]".formatted(regionPos.toChunk(bit).x(), regionPos.toChunk(bit).z()));
				return List.of();
			}
			for (BitSet splitChunks : BitSetUtil.half(set)) {
				payloads.addAll(new S2CUpdateRegionPacket(dimension, shared, regionPos, biomePalette, blockPalette, splitChunks, ListUtil.splitSet(chunks, splitChunks, set)).toPayloads(registryManager));
			}
		}
		return payloads;
	}

	@Override
	public Id<S2CUpdateRegionPacket> getId() {
		return ID;
	}
}
