package folk.sisby.surveyor.packet;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.structure.StructureStartSummary;
import folk.sisby.surveyor.structure.WorldStructures;
import folk.sisby.surveyor.util.MapUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record S2CStructuresAddedPacket(RegistryKey<World> dimension, boolean shared, Table<RegistryKey<Structure>, ChunkPos, StructureStartSummary> starts, Map<RegistryKey<Structure>, RegistryKey<StructureType<?>>> types, Multimap<RegistryKey<Structure>, TagKey<Structure>> tags) implements S2CPacket {
	public static final CustomPayload.Id<S2CStructuresAddedPacket> ID = new CustomPayload.Id<>(Surveyor.id("s2c_structures_added"));
	public static final PacketCodec<PacketByteBuf, S2CStructuresAddedPacket> CODEC = PacketCodec.tuple(
		RegistryKey.createPacketCodec(RegistryKeys.WORLD), S2CStructuresAddedPacket::dimension,
		PacketCodecs.BOOL, S2CStructuresAddedPacket::shared,
		SurveyorPacketCodecs.STRUCTURE_SUMMARIES, S2CStructuresAddedPacket::starts,
		SurveyorPacketCodecs.STRUCTURE_TYPES, S2CStructuresAddedPacket::types,
		SurveyorPacketCodecs.STRUCTURE_TAGS, S2CStructuresAddedPacket::tags,
		S2CStructuresAddedPacket::new
	);

	public static S2CStructuresAddedPacket of(boolean shared, Multimap<RegistryKey<Structure>, ChunkPos> starts, WorldStructures structures) {
		return structures.createUpdatePacket(shared, starts);
	}

	public static S2CStructuresAddedPacket of(boolean shared, RegistryKey<Structure> key, ChunkPos pos, WorldStructures structures) {
		return of(shared, MapUtil.asMultiMap(Map.of(key, List.of(pos))), structures);
	}

	@Override
	public List<SurveyorPacket> toPayloads() {
		List<SurveyorPacket> payloads = new ArrayList<>();
		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		CODEC.encode(buf, this);
		if (buf.readableBytes() < MAX_PAYLOAD_SIZE) {
			payloads.add(this);
		} else {
			Multimap<RegistryKey<Structure>, ChunkPos> keySet = MapUtil.keyMultiMap(starts);
			if (keySet.size() == 1) {
				Surveyor.LOGGER.error("Couldn't create a structure update packet for {} at {} - an individual structure would be too large to send!", keySet.keys().stream().findFirst().orElseThrow().getValue(), keySet.values().stream().findFirst().orElseThrow());
				return List.of();
			}
			Multimap<RegistryKey<Structure>, ChunkPos> firstHalf = HashMultimap.create();
			Multimap<RegistryKey<Structure>, ChunkPos> secondHalf = HashMultimap.create();
			keySet.forEach((key, pos) -> {
				if (firstHalf.size() < keySet.size() / 2) {
					firstHalf.put(key, pos);
				} else {
					secondHalf.put(key, pos);
				}
			});
			payloads.addAll(new S2CStructuresAddedPacket(dimension, shared, MapUtil.splitByKeyMap(starts, firstHalf), MapUtil.splitByKeySet(types, firstHalf.keySet()), MapUtil.asMultiMap(MapUtil.splitByKeySet(tags.asMap(), firstHalf.keySet()))).toPayloads());
			payloads.addAll(new S2CStructuresAddedPacket(dimension, shared, MapUtil.splitByKeyMap(starts, secondHalf), MapUtil.splitByKeySet(types, secondHalf.keySet()), MapUtil.asMultiMap(MapUtil.splitByKeySet(tags.asMap(), secondHalf.keySet()))).toPayloads());
		}
		return payloads;
	}

	@Override
	public CustomPayload.Id<S2CStructuresAddedPacket> getId() {
		return ID;
	}
}
