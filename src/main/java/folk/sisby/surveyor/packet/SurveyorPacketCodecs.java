package folk.sisby.surveyor.packet;

import com.google.common.collect.Multimap;
import com.mojang.serialization.Codec;
import folk.sisby.surveyor.PlayerSummary;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.Landmarks;
import folk.sisby.surveyor.structure.RegionStructureSummary;
import folk.sisby.surveyor.structure.StructurePieceSummary;
import folk.sisby.surveyor.structure.StructureStartSummary;
import folk.sisby.surveyor.util.MapUtil;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface SurveyorPacketCodecs {
	PacketCodec<PacketByteBuf, Map<ChunkPos, BitSet>> TERRAIN_KEYS = PacketCodecs.map(HashMap::new,
		PacketCodecs.VAR_LONG.xmap(ChunkPos::new, ChunkPos::toLong),
		PacketCodecs.codec(Codecs.BIT_SET)
	);

	PacketCodec<PacketByteBuf, Multimap<RegistryKey<Structure>, ChunkPos>> STRUCTURE_KEYS = PacketCodecs.<PacketByteBuf, RegistryKey<Structure>, List<ChunkPos>, Map<RegistryKey<Structure>, List<ChunkPos>>>map(HashMap::new,
		RegistryKey.createPacketCodec(RegistryKeys.STRUCTURE),
		PacketCodecs.VAR_LONG.xmap(ChunkPos::new, ChunkPos::toLong).collect(PacketCodecs.toList())
	).xmap(MapUtil::asMultiMap, MapUtil::asListMap);

	PacketCodec<PacketByteBuf, Map<RegistryKey<Structure>, LongSet>> STRUCTURE_KEYS_LONG_SET = PacketCodecs.map(HashMap::new,
		RegistryKey.createPacketCodec(RegistryKeys.STRUCTURE),
		PacketCodecs.codec(Codec.LONG_STREAM).xmap(LongOpenHashSet::toSet, LongSet::longStream)
	);

	PacketCodec<PacketByteBuf, Multimap<UUID, Identifier>> LANDMARK_KEYS = PacketCodecs.<PacketByteBuf, UUID, List<Identifier>, Map<UUID, List<Identifier>>>map(HashMap::new,
		Uuids.PACKET_CODEC,
		Identifier.PACKET_CODEC.collect(PacketCodecs.toList())
	).xmap(MapUtil::asMultiMap, MapUtil::asListMap);

	PacketCodec<RegistryByteBuf, Map<UUID, PlayerSummary>> GROUP_SUMMARIES = PacketCodecs.map(HashMap::new,
		Uuids.PACKET_CODEC,
		PacketCodec.of(PlayerSummary.OfflinePlayerSummary::writeBuf, PlayerSummary.OfflinePlayerSummary::readBuf)
	);

	PacketCodec<PacketByteBuf, Map<RegistryKey<Structure>, Map<ChunkPos, StructureStartSummary>>> STRUCTURE_SUMMARIES = PacketCodecs.map(HashMap::new,
		RegistryKey.createPacketCodec(RegistryKeys.STRUCTURE),
		PacketCodecs.map(HashMap::new,
			PacketCodecs.VAR_LONG.xmap(ChunkPos::new, ChunkPos::toLong),
			PacketCodec.of((StructurePieceSummary s, PacketByteBuf b) -> b.writeNbt(s.toNbt()), (PacketByteBuf b) -> RegionStructureSummary.readStructurePieceNbt(b.readNbt())).collect(PacketCodecs.toList()).xmap(StructureStartSummary::new, StructureStartSummary::getChildren)
		)
	);

	PacketCodec<PacketByteBuf, Map<RegistryKey<Structure>, RegistryKey<StructureType<?>>>> STRUCTURE_TYPES = PacketCodecs.map(HashMap::new,
		RegistryKey.createPacketCodec(RegistryKeys.STRUCTURE),
		RegistryKey.createPacketCodec(RegistryKeys.STRUCTURE_TYPE)
	);

	PacketCodec<PacketByteBuf, Multimap<RegistryKey<Structure>, TagKey<Structure>>> STRUCTURE_TAGS = PacketCodecs.<PacketByteBuf, RegistryKey<Structure>, List<TagKey<Structure>>, Map<RegistryKey<Structure>, List<TagKey<Structure>>>>map(HashMap::new,
		RegistryKey.createPacketCodec(RegistryKeys.STRUCTURE),
		PacketCodecs.codec(TagKey.codec(RegistryKeys.STRUCTURE)).collect(PacketCodecs.toList())
	).xmap(MapUtil::asMultiMap, MapUtil::asListMap);

	PacketCodec<ByteBuf, Map<UUID, Map<Identifier, Landmark>>> LANDMARK_SUMMARIES = PacketCodecs.codec(Landmarks.CODEC);
}
