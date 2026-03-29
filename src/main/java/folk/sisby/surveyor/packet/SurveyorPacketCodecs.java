package folk.sisby.surveyor.packet;

import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.mojang.serialization.Codec;
import folk.sisby.surveyor.PlayerSummary;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.structure.RegionStructureSummary;
import folk.sisby.surveyor.structure.StructurePieceSummary;
import folk.sisby.surveyor.structure.StructureStartSummary;
import folk.sisby.surveyor.util.MapUtil;
import folk.sisby.surveyor.util.RegionPos;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
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
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface SurveyorPacketCodecs {
	PacketCodec<RegistryByteBuf, Table<RegistryKey<World>, RegionPos, BitSet>> TERRAIN_KEYS = PacketCodecs.<RegistryByteBuf, RegistryKey<World>, Map<RegionPos, BitSet>, Map<RegistryKey<World>, Map<RegionPos, BitSet>>>map(HashMap::new,
		RegistryKey.createPacketCodec(RegistryKeys.WORLD),
		PacketCodecs.map(HashMap::new,
			RegionPos.PACKET_CODEC,
			PacketCodecs.codec(Codecs.BIT_SET)
		)
	).xmap(MapUtil::asTable, Table::rowMap);

	PacketCodec<RegistryByteBuf, Map<RegistryKey<World>, Multimap<RegistryKey<Structure>, ChunkPos>>> STRUCTURE_KEYS = PacketCodecs.map(HashMap::new,
		RegistryKey.createPacketCodec(RegistryKeys.WORLD), PacketCodecs.<RegistryByteBuf, RegistryKey<Structure>, List<ChunkPos>, Map<RegistryKey<Structure>, List<ChunkPos>>>map(HashMap::new,
			RegistryKey.createPacketCodec(RegistryKeys.STRUCTURE),
			PacketCodecs.VAR_LONG.xmap(ChunkPos::fromLong, ChunkPos::toLong).collect(PacketCodecs.toList())
		).xmap(MapUtil::asMultiMap, MapUtil::asListMap)
	);

	PacketCodec<RegistryByteBuf, Table<RegistryKey<World>, RegistryKey<Structure>, LongSet>> STRUCTURE_KEYS_LONG_SET = PacketCodecs.<RegistryByteBuf, RegistryKey<World>, Map<RegistryKey<Structure>, LongSet>, Map<RegistryKey<World>, Map<RegistryKey<Structure>, LongSet>>>map(HashMap::new,
		RegistryKey.createPacketCodec(RegistryKeys.WORLD),
		PacketCodecs.map(HashMap::new,
			RegistryKey.createPacketCodec(RegistryKeys.STRUCTURE),
			PacketCodecs.codec(Codec.LONG_STREAM).xmap(LongOpenHashSet::toSet, LongSet::longStream)
		)
	).xmap(MapUtil::asTable, Table::rowMap);

	PacketCodec<RegistryByteBuf, Map<RegistryKey<World>, Multimap<UUID, Identifier>>> LANDMARK_KEYS = PacketCodecs.map(HashMap::new,
		RegistryKey.createPacketCodec(RegistryKeys.WORLD),
		PacketCodecs.<RegistryByteBuf, UUID, List<Identifier>, Map<UUID, List<Identifier>>>map(HashMap::new,
			Uuids.PACKET_CODEC,
			Identifier.PACKET_CODEC.collect(PacketCodecs.toList())
		).xmap(MapUtil::asMultiMap, MapUtil::asListMap)
	);

	PacketCodec<RegistryByteBuf, Map<UUID, PlayerSummary>> GROUP_SUMMARIES = PacketCodecs.map(HashMap::new,
		Uuids.PACKET_CODEC,
		PacketCodec.of(PlayerSummary.OfflinePlayerSummary::writeBuf, PlayerSummary.OfflinePlayerSummary::readBuf)
	);

	PacketCodec<RegistryByteBuf, Table<RegistryKey<Structure>, ChunkPos, StructureStartSummary>> STRUCTURE_SUMMARIES = PacketCodecs.<RegistryByteBuf, RegistryKey<Structure>, Map<ChunkPos, StructureStartSummary>, Map<RegistryKey<Structure>, Map<ChunkPos, StructureStartSummary>>>map(HashMap::new,
		RegistryKey.createPacketCodec(RegistryKeys.STRUCTURE),
		PacketCodecs.map(HashMap::new,
			PacketCodecs.VAR_LONG.xmap(ChunkPos::fromLong, ChunkPos::toLong),
			PacketCodec.of((StructurePieceSummary s, RegistryByteBuf b) -> b.writeNbt(s.toNbt()), (RegistryByteBuf b) -> RegionStructureSummary.readStructurePieceNbt(b.readNbt())).collect(PacketCodecs.toList()).xmap(StructureStartSummary::new, StructureStartSummary::getChildren)
		)
	).xmap(MapUtil::asTable, Table::rowMap);

	PacketCodec<RegistryByteBuf, Map<RegistryKey<Structure>, RegistryKey<StructureType<?>>>> STRUCTURE_TYPES = PacketCodecs.map(HashMap::new,
		RegistryKey.createPacketCodec(RegistryKeys.STRUCTURE),
		RegistryKey.createPacketCodec(RegistryKeys.STRUCTURE_TYPE)
	);

	PacketCodec<RegistryByteBuf, Multimap<RegistryKey<Structure>, TagKey<Structure>>> STRUCTURE_TAGS = PacketCodecs.<RegistryByteBuf, RegistryKey<Structure>, List<TagKey<Structure>>, Map<RegistryKey<Structure>, List<TagKey<Structure>>>>map(HashMap::new,
		RegistryKey.createPacketCodec(RegistryKeys.STRUCTURE),
		PacketCodecs.codec(TagKey.codec(RegistryKeys.STRUCTURE)).collect(PacketCodecs.toList())
	).xmap(MapUtil::asMultiMap, MapUtil::asListMap);

	PacketCodec<ByteBuf, Table<UUID, Identifier, Landmark>> LANDMARK_SUMMARIES = PacketCodecs.codec(WorldLandmarks.CODEC);
}
